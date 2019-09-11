properties([
    buildDiscarder(
        logRotator(daysToKeepStr: '', numToKeepStr: '100', artifactDaysToKeepStr: '', artifactNumToKeepStr: '1')
    ),
    parameters([
        string(name: 'APP_NAME', defaultValue: 'crmfe', description: '', trim: true),
        string(name: 'REPO_URL', defaultValue: 'ssh://git@code.bkjk-inc.com:32222/hcrm/crm-fe.git', description: '', trim: true),
        string(name: 'REPO_NAME', defaultValue: 'crm-fe', description: '', trim: true),
        string(name: 'BRANCH', defaultValue: 'master', description: '', trim: true),
        string(name: 'IMAGE', defaultValue: 'harbor.ocean.bkjk-inc.com/test/crmfe', description: '', trim: true),
        string(name: 'IMAGE_VERSION', defaultValue: 'v0.1', description: '', trim: true),
        string(name: 'NO_CACHE', defaultValue: 'false', description: '', trim: true),
        string(name: 'CODE_ROOT', defaultValue: '.', description: 'Dockerfile使用的代码的root目录，比如repo是 myapp，里面有myapp/frontend和myapp/backend, 我想打 myapp-backend镜像，那么应该进到myapp/backend里面，CODE_ROOT应该是 backend', trim: true),
        string(name: 'BUILD_CMD', defaultValue: 'npm install && npm run dll && npm run build', description: '应用构建命令', trim: true),
        string(name: 'START_CMD', defaultValue: 'npm run start dev', description: '', trim: true),
        string(name: 'ENV', defaultValue: 'dev', description: '', trim: true),
        string(name: 'BASE_IMAGE', defaultValue: 'harbor.ocean.bkjk-inc.com/base/node:8', description: '', trim: true),
        choice(name: 'NODE_VERSION', choices: ['8', '9', '10'], description: 'NODE VERSION 支持， 8，9，10， 默认8')
    ])
])

ansiColor('xterm') {
node {
    String globalDockerRegistry = "https://harbor.ocean.bkjk-inc.com"// DOCKER_REGISTRY
    String globalCredentialsID = '075b9b9b-85d5-4619-be19-40872b8d96ad' // CREDENTIALS_ID
    String globalGitCommit = ''
    String globalCommitDate = ''

    String globalNodeVersion = params.NODE_VERSION
    String globalBranch = params.BRANCH
    String globalRepoUrl = params.REPO_URL
    String globalCodeRoot = params.CODE_ROOT
    String globalEnv = params.ENV
    String globalBuildCmd = params.BUILD_CMD
    String globalBaseImage = params.BASE_IMAGE
    String globalStartCmd = params.START_CMD
    String globalNoCache = params.NO_CACHE
    String globalVersionImage = params.IMAGE + ":" + params.IMAGE_VERSION + "." + BUILD_ID


    stage('CheckArg') {
        if (globalNodeVersion == '10') {
            env.NODE_HOME = tool name: 'node-10.13', type: 'nodejs'
        } else if (globalNodeVersion == '9') {
            env.NODE_HOME = tool name: 'node-9.4', type: 'nodejs'
        } else {
            env.NODE_HOME = tool name: 'node-8.11', type: 'nodejs'
        }
        env.PATH = "${env.NODE_HOME}/bin:${env.PATH}"
    }

    stage('CloneCode') {
        checkout([
                $class: 'GitSCM', branches:
                 [[name: globalBranch]],
                 doGenerateSubmoduleConfigurations: false,
                 extensions: [],
                 submoduleCfg: [],
                 userRemoteConfigs: [[credentialsId: 'c9fa0ecc-6030-49ad-9877-dbecbf05a34e', url: globalRepoUrl]]]
        )
        globalGitCommit = sh(script: "git rev-parse HEAD", returnStdout: true).trim()
        globalCommitDate = sh(script: "git log ${globalGitCommit} -n 1 --pretty=format:\"%cd\" --date=iso", , returnStdout: true).trim()
    }

    stage('CompileCode') {
        docker.withTool("docker-18") {
            docker.withRegistry("${globalDockerRegistry}", "${globalCredentialsID}") {
                sh("""
                
                node -v; npm -v
                cd ${globalCodeRoot}
                
                printf '\n' >> .npmrc
                echo "strict-ssl = false" >> .npmrc
                echo "unsafe-perm = true" >> .npmrc
                echo "cache-lock-wait = 300000" >> .npmrc
                echo "cache-max = 3600" >> .npmrc
                echo "progress = false" >> .npmrc

                NODE_ENV=${globalEnv}
                echo "${globalBuildCmd}" | cat -A
                ${globalBuildCmd} 
                """)
            }
        }
    }



    stage('BuildImage') {
        docker.withTool("docker-18") {
            docker.withRegistry("${globalDockerRegistry}", "${globalCredentialsID}") {
                sh("""
                cd ${globalCodeRoot}
                
                echo 'FROM ${globalBaseImage}' > Dockerfile
                echo 'WORKDIR /opt/app' >> Dockerfile
                echo 'COPY . .' >> Dockerfile
                echo \"ENV GIT_COMMIT=`git rev-parse HEAD` BUILD_TIME=`date +%Y-%m-%d_%H:%M:%S` NODE_ENV=${ENV}\" >> Dockerfile
                echo "LABEL GIT_COMMIT=${globalGitCommit} COMMIT_DATE=\\"${globalCommitDate}\\" BUILD_TIME=`date +%Y-%m-%d_%H:%M:%S` GIT_BRANCH=${params.BRANCH}" >> Dockerfile
                echo 'CMD ${globalStartCmd}' >> Dockerfile
                

                echo .git >> .dockerignore
                docker pull ${globalBaseImage}  && docker build . --no-cache=${globalNoCache} -t ${globalVersionImage} && docker push ${globalVersionImage}
                """)
            }
        }
    }



}
}




