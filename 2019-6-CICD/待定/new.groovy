import groovy.json.JsonOutput

ansiColor('xterm') {

 properties([
        buildDiscarder(logRotator(daysToKeepStr: '', numToKeepStr: '100', artifactDaysToKeepStr: '', artifactNumToKeepStr: '1')),
        disableConcurrentBuilds(),
        [$class: 'RebuildSettings', autoRebuild: false, rebuildDisabled: false],


        parameters([
            string(name: 'DIST_DIR', defaultValue: 'dist', trim: true, description: 'dist dir'),
            string(name: 'BASE_IMAGE', defaultValue: 'harbor.ocean.bkjk-inc.com/base/nginx-spa:1.14-alpine', trim: true, description: '基础镜像'),
            string(name: 'REPO_URL', defaultValue: 'ssh://git@code.bkjk-inc.com:32222/hotpot/hotpotservice.git', trim: true, description: '仓库地址'),
            booleanParam(name: 'NO_CACHE', defaultValue: false, description: 'no cache'),
            string(name: 'IMAGE', defaultValue: 'harbor.ocean.bkjk-inc.com/bkjk-hotpot/hotpotfrontend:dev-dev', trim: true, description: '目标镜像'),
            string(name: 'CODE_ROOT', defaultValue: './frontend-ui', trim: true, description: '.'),
            string(name: 'NODE_VERSION', defaultValue: '8', trim: true, description: 'nodejs version'),
            string(name: 'BRANCH', defaultValue: 'dev', trim: true, description: '分支'),
            string(name: 'BUILD_CMD', defaultValue: 'npm install && npm run build:dev', trim: true, description: '应用构建命令'),
            string(name: 'REPO_NAME', defaultValue: 'hotpotservice', trim: true, description: 'repo name'),
            string(name: 'ENV', defaultValue: 'dev', trim: true, description: '构建环境')
        ]),
    ])

node() {
   
   String globalDockerRegistry = "https://harbor.ocean.bkjk-inc.com"    // DOCKER_REGISTRY
   String globalCredentialsID = '075b9b9b-85d5-4619-be19-40872b8d96ad' // CREDENTIALS_ID
   String globalDistDir = params.DIST_DIR// DIST_DIR
   String globalBaseImage = params.BASE_IMAGE //BASE_IMAGE
   String globalRepoURL = params.REPO_URL
   Boolean globalNoCache = params.NO_CACHE
   String globalImage = params.IMAGE
   String globalCodeRoot = params.CODE_ROOT
   String globalNodeVersion = params.NODE_VERSION
   String globalBranch = params.BRANCH
   String globalBuildCmd = params.BUILD_CMD
   String globalRepoName = params.REPO_NAME
   String globalEnv = params.ENV

   String globalShortCommit = ""

   JOB_ROOT = pwd()
    


    def callbackBody = [
        app: env.JOB_BASE_NAME,
        buildID: env.BUILD_ID.toInteger(),
        status: "SUCCESS",
    ]

    try {

        stage('CheckArg') {
            switch (params.NODE_VERSION) {
                case '10':
                    env.NODE_HOME = tool name: 'node-10.7', type: 'nodejs'
                    break
                case '9':
                    env.NODE_HOME = tool name: 'node-9.4', type: 'nodejs'
                    break
                case '6':
                    env.NODE_HOME = tool name: 'node-6.14', type: 'nodejs'
                    break
                default:
                    env.NODE_HOME = tool name: 'node-8.11', type: 'nodejs'
                    break
            }

            env.PATH = "${env.NODE_HOME}/bin:${env.PATH}"
        }
       
        stage('CloneCode') {
            dir(globalRepoName) {
                checkout([
                    $class: 'GitSCM', branches:[[name: globalBranch]],
                    doGenerateSubmoduleConfigurations: false,
                    extensions: [],
                    submoduleCfg: [],
                    userRemoteConfigs: [[credentialsId: 'c9fa0ecc-6030-49ad-9877-dbecbf05a34e', url: globalRepoURL]]]
                )
                globalShortCommit = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
            }
        }

        stage('SonarCheck') {
        }

        stage('CompileCode') {
            docker.withTool("docker-18") {
                docker.withRegistry("${globalDockerRegistry}", "${globalCredentialsID}") {
                    sh("""
                        set -e
                
                        export NODE_ENV=${globalEnv}
                        echo ${NODE_HOME}; node -v ; npm -v
                
                        cd ${globalRepoName} && cd ${globalCodeRoot} && ${globalBuildCmd}
                    """)
                }
            }
        }

        stage('BuildImage') {
            docker.withTool("docker-18") {
                docker.withRegistry("${globalDockerRegistry}", "${globalCredentialsID}") {
                    sh("""
                        cd ${globalRepoName} && cd ${globalCodeRoot} && ${globalBuildCmd}

                        echo "FROM ${globalBaseImage}" > Dockerfile
                        echo "ENV GIT_COMMIT=`git rev-parse HEAD` BUILD_TIME=`date +%Y-%m-%d_%H:%M:%S`" >> Dockerfile
                        echo "COPY ${globalDistDir}/ ./" >> Dockerfile

                        echo -ne '*\n!'${globalDistDir}'\n' > .dockerignore
                        echo .git >> .dockerignore
                        docker build . --no-cache=${globalNoCache} -t ${globalImage} && docker push ${globalImage}
                    """)
                }
            }
        }
       
	} catch (err) {
	    callbackBody.status = "FAILURE"
	    callbackBody.error = err.getMessage()
        httpRequest (
            url: "http://10.11.209.103:8080/pub/jenkins/callback",
            httpMode: 'POST',
            ignoreSslErrors: true,
            contentType: 'APPLICATION_JSON',
            timeout: 90,
            requestBody: JsonOutput.toJson(callbackBody),
            responseHandle: 'NONE'
        )
        throw err
    }
}
}