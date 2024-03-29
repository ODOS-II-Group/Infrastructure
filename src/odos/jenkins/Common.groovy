def runGitMerge(String git_branch, String base_branch){
  sh returnStdout: true, script: """
    git checkout ${git_branch}
    git pull -ff origin ${base_branch}
    git pull origin ${git_branch}
 """
 println "Locally merged $base_branch to $git_branch"
}

/****PUSHES A BRANCH UP****/
def runGitPush(git_branch){
	println "trying to push branch ... $git_branch to origin"
  sh returnStdout: true, script: """
    git tag -a -f -m "Jenkins Build #${BUILD_ID}" jenkins-merge-${BUILD_ID}
    git --version
    git push origin HEAD:${git_branch}
  """
	println "Pushed $git_branch to repository"
}

def slack(String msg){
  echo msg
  slackSend botUser: true, message: "${JOB_NAME}#${BUILD_ID}: ${msg}", tokenCredentialId: 'slack'

}

def jHipsterBuild(){
  sh './gradlew clean bootRepackage -Pprod --stacktrace'
}

def sonarScan(Boolean break_build=false){
  //TODO: build breaking
  sh './gradlew sonarqube --stacktrace'
}

def buildContainer(String containerName){
  withCredentials([usernamePassword(credentialsId: 'docker', passwordVariable: 'ODOS_PW', usernameVariable: 'ODOS_USER')]) {
      sh """
        docker login -u ${ODOS_USER} -p ${ODOS_PW} docker.lassiterdynamics.com:5000
        ./gradlew buildDocker
        docker tag ${containerName}:latest docker.lassiterdynamics.com:5000/${containerName}:latest
        docker tag docker.lassiterdynamics.com:5000/${containerName}:latest docker.lassiterdynamics.com:5000/${containerName}:${BUILD_ID}
      """
  }
}

def pushContainer(String containerName){
  sh "docker push docker.lassiterdynamics.com:5000/${containerName}:${BUILD_ID}"
  sh "docker push docker.lassiterdynamics.com:5000/${containerName}:latest"
}

def twistlock(String repo,String image,String tag){
  try{
    twistlockScan(
      ca: '',
      cert: '',
      compliancePolicy: 'high',
      dockerAddress: 'unix:///var/run/docker.sock',
      gracePeriodDays: 0,
      ignoreImageBuildTime: false,
      repository: repo,
      image: "${repo}/${image}:${tag}",
      tag: tag,
      key: '',
      logLevel: 'true',
      policy: 'warn',
      requirePackageUpdate: true,
      timeout: 10
    )
  }
  finally {
    twistlockPublish(
      ca: '',
      cert: '',
      dockerAddress: 'unix:///var/run/docker.sock',
      image: "${repo}/${image}:${tag}",
      key: '',
      logLevel: 'true',
      timeout: 10
    )
  }
}

def deployToOpenShift(String environment, String image, String tag){
  withCredentials([string(credentialsId: 'odos-jenkins-token', variable: 'OCP_TOKEN')]) {
    sh """
    oc login https://api.pro-us-east-1.openshift.com --token=${OCP_TOKEN}
    oc project ${environment}

    oc import-image ${image} \
      --from='docker.lassiterdynamics.com:5000/${image}:${tag}' \
      --confirm
    """
  }
}

def fortify(srcDir,reportDir, appID=0){
  fpr="${reportDir}/${JOB_BASE_NAME}-${BUILD_NUMBER}.fpr".replaceAll("[^a-zA-Z0-9-_\\./]","_")
  pdf="${reportDir}/${JOB_BASE_NAME}-${BUILD_NUMBER}.pdf".replaceAll("[^a-zA-Z0-9-_\\./]","_")
  if( appID ) {
    withCredentials([string(credentialsId:'fortifyDLToken',variable:'FORTIFY_DL_TOKEN')]){
      sh """
        export PATH=$PATH:$FORTIFY_BIN
        mkdir -p ${reportDir}
        fortifyclient downloadFPR \
          -url ${FORTIFY_URL} \
          -authtoken ${FORTIFY_DL_TOKEN} \
          -applicationVersionID ${appID} \
          -file ${fpr} || true
      """
    }
  }
  sh """
    export PATH=$PATH:$FORTIFY_BIN
    sourceanalyzer -clean

    sourceanalyzer \
      -build-label ${JOB_BASE_NAME}-${BUILD_NUMBER} \
      -scan \
      -f ${fpr} \
      -logfile ${reportDir}/FortifyScan.log \
      ${srcDir}

    ReportGenerator \
      -template ScanReport.xml \
      -format pdf \
      -source ${fpr} \
      -f ${pdf}
  """

  if( appID ) {
    withCredentials([string(credentialsId:'fortifyULToken',variable:'FORTIFY_UL_TOKEN')]){
      sh """
        export PATH=$PATH:$FORTIFY_BIN
        fortifyclient uploadFPR \
          -url ${FORTIFY_URL} \
          -authtoken ${FORTIFY_UL_TOKEN} \
          -applicationVersionID ${appID} \
          -file ${fpr}
      """
    }
  }

  archiveArtifacts allowEmptyArchive: true, artifacts: "${reportDir}/*", fingerprint: true
}

def runFT( targetURL ){

}

def runPT( targetURL ){

}

return this
