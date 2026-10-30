// Declarative CI/CD pipeline: Checkout -> Build -> Test -> Docker Build -> Push -> Deploy
pipeline {
    agent any

    tools {
        jdk 'JDK-21'
        maven 'Maven-3.9'
    }

    environment {
        // Jenkins credential (Username + Docker Hub access token).
        // Exposes DOCKERHUB_USR / DOCKERHUB_PSW, masked in the console log.
        DOCKERHUB = credentials('dockerhub-creds')
        IMAGE_NAME = 'calculator-api'
        IMAGE_TAG = "${env.BUILD_NUMBER}"
        COMPOSE_PROJECT_NAME = 'calculator'
        PROXY_PORT = '8090'
    }

    triggers {
        githubPush()                 // GitHub webhook -> /github-webhook/
        pollSCM('H/5 * * * *')       // fallback if the webhook can't reach Jenkins
    }

    options {
        skipDefaultCheckout(true)
        disableConcurrentBuilds()    // two deploys must never race on the same stack
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '15'))
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                sh 'git log -1 --oneline'
            }
        }

        stage('Build') {
            steps {
                sh 'mvn -B clean package -DskipTests'
            }
        }

        stage('Test') {
            steps {
                sh 'mvn -B test'
            }
            post {
                always {
                    junit 'target/surefire-reports/*.xml'
                }
            }
        }

        stage('Docker Build') {
            steps {
                sh '''
                    IMAGE="$DOCKERHUB_USR/$IMAGE_NAME"
                    docker build -t "$IMAGE:$IMAGE_TAG" -t "$IMAGE:latest" .
                '''
            }
        }

        stage('Push') {
            steps {
                sh '''
                    IMAGE="$DOCKERHUB_USR/$IMAGE_NAME"
                    echo "$DOCKERHUB_PSW" | docker login -u "$DOCKERHUB_USR" --password-stdin
                    docker push "$IMAGE:$IMAGE_TAG"
                    docker push "$IMAGE:latest"
                '''
            }
        }

        stage('Deploy') {
            steps {
                // "up -d" only recreates containers whose image/config changed, so re-runs are safe;
                // "--wait" blocks until every health check passes (fails the stage otherwise).
                sh '''
                    export DOCKER_IMAGE="$DOCKERHUB_USR/$IMAGE_NAME"
                    docker compose up -d --wait --wait-timeout 180 --remove-orphans
                    docker compose ps
                '''
                // Smoke test through the Nginx proxy
                sh '''
                    curl -fsS --retry 10 --retry-delay 3 --retry-all-errors "http://localhost:$PROXY_PORT/actuator/health"
                    curl -fsS "http://localhost:$PROXY_PORT/api/v1/add?a=2&b=3" | grep -q '"result":5.0'
                    echo "Deployed $DOCKERHUB_USR/$IMAGE_NAME:$IMAGE_TAG - smoke test passed"
                '''
            }
        }
    }

    post {
        success {
            emailext(
                to: '$DEFAULT_RECIPIENTS',
                subject: "SUCCESS: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                mimeType: 'text/html',
                body: """<p>Build <b>#${env.BUILD_NUMBER}</b> of <b>${env.JOB_NAME}</b> succeeded.</p>
                         <p>Image: <code>${env.DOCKERHUB_USR}/${env.IMAGE_NAME}:${env.IMAGE_TAG}</code> deployed on port ${env.PROXY_PORT}.</p>
                         <p><a href="${env.BUILD_URL}">Open build</a></p>"""
            )
        }
        failure {
            emailext(
                to: '$DEFAULT_RECIPIENTS',
                subject: "FAILURE: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                mimeType: 'text/html',
                attachLog: true,
                body: """<p>Build <b>#${env.BUILD_NUMBER}</b> of <b>${env.JOB_NAME}</b> failed.</p>
                         <p>If it failed before the Deploy stage, the previously deployed version is still running. Console log attached.</p>
                         <p><a href="${env.BUILD_URL}console">Open console</a></p>"""
            )
        }
        always {
            sh 'docker logout || true'
            sh 'docker image prune -f || true'
        }
    }
}
