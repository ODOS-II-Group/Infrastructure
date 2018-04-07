
def runGitMerge(String git_branch, String base_branch){
  sh returnStdout: true, script: """
    git checkout ${git_branch}
    git pull origin ${base_branch}
 """
 println "Locally merged $base_branch to $git_branch"
}

/****PUSHES A BRANCH UP****/
def runGitPush(git_branch){
	println "trying to push branch ... $git_branch to origin"
  sh returnStdout: true, script: """
    git tag -a -f -m "Jenkins Build #${BUILD_ID}" jenkins-merge-${BUILD_ID}
    git --version
    git push origin ${git_branch}
  """
	println "Pushed $git_branch to repository"
}
void slack(String msg){
  echo msg
  slackSend botUser: true, message: "${JOB_NAME}#${BUILD_ID}: ${msg}", tokenCredentialId: 'slack'

}

def buildContainer(String containerName){
  withCredentials([usernamePassword(credentialsId: 'odos-password', passwordVariable: 'ODOS_PW', usernameVariable: 'ODOS_USER')]) {
      sh """
        docker login -u ${ODOS_USER} -p ${ODOS_PW} docker.lassiterdynamics.com:5000
        ./gradlew buildDocker
        docker tag docker.lassiterdynamics.com:5000/${containerName}:latest docker.lassiterdynamics.com:5000/${containerName}:${BUILD_ID}
      """
  }
}

def twistlock(String repo,String image,String tag){
  twistlockScan(
    ca: '',
    cert: '',
    compliancePolicy: 'high',
    dockerAddress: 'unix:///var/run/docker.sock',
    gracePeriodDays: 0,
    ignoreImageBuildTime: false,
    repository: repo,
    image: "${image}:${tag}",
    tag: tag,
    key: '',
    logLevel: 'true',
    policy: 'high',
    requirePackageUpdate: true,
    timeout: 10
  )

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

return this
