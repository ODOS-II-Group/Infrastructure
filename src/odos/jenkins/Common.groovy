
def runGitMergeFromBranch(String git_branch, String git_base_branch, String git_repo_url){
 checkout changelog: false, poll: false, scm: [
     $class: 'GitSCM',
     branches: [[name: git_base_branch]],
     doGenerateSubmoduleConfigurations: false,
     extensions: [[
         $class: 'PreBuildMerge',
         options: [fastForwardMode: 'FF', mergeRemote: 'origin', mergeStrategy: 'recursive', mergeTarget: git_branch]
     ]],
     submoduleCfg: [],
     userRemoteConfigs: [[credentialsId: 'jenkins-ssh', url: git_repo_url]]
 ]
 println "Locally merged $git_base_branch to $git_branch"
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
