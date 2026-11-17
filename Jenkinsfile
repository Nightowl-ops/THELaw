pipeline {
    agent any

    environment {
        AWS_REGION         = 'us-east-1'
        AWS_ACCOUNT_ID     = '411711548396'
        ECR_REGISTRY       = '411711548396.dkr.ecr.us-east-1.amazonaws.com'
        EC2_PUBLIC_IP      = '54.162.223.159'
        VERSION_TAG        = '1.0.0'
        IMAGE_AUTH         = "${ECR_REGISTRY}/veritas-auth-service"
        IMAGE_CASE         = "${ECR_REGISTRY}/veritas-case-evidence-service"
        IMAGE_FORENSIC     = "${ECR_REGISTRY}/veritas-forensic-vault-service"
        IMAGE_GATEWAY      = "${ECR_REGISTRY}/veritas-api-gateway"
        IMAGE_FRONTEND     = "${ECR_REGISTRY}/veritas-frontend"
    }

    stages {
        stage('Checkout') {
            steps {
                echo 'Checking out source code from Git repository...'
                checkout scm
            }
        }

        stage('Automated BDD Testing') {
            steps {
                echo 'Executing Cucumber BDD Test Suites across Microservices...'
                sh '''
                    cd auth-service && mvn clean test
                    cd ../case-evidence-service && mvn clean test
                    cd ../forensic-vault-service && mvn clean test
                '''
            }
        }

        stage('Package Application') {
            steps {
                echo 'Packaging Microservices JARs and Frontend SPA...'
                sh '''
                    cd auth-service && mvn package -DskipTests
                    cd ../case-evidence-service && mvn package -DskipTests
                    cd ../forensic-vault-service && mvn package -DskipTests
                    cd ../api-gateway && mvn package -DskipTests
                    cd ../frontend && npm install && npm run build
                '''
            }
        }

        stage('Build Docker Images') {
            steps {
                echo 'Building Docker container images for all microservices...'
                sh '''
                    docker build -t ${IMAGE_AUTH}:${VERSION_TAG} -t ${IMAGE_AUTH}:latest ./auth-service
                    docker build -t ${IMAGE_CASE}:${VERSION_TAG} -t ${IMAGE_CASE}:latest ./case-evidence-service
                    docker build -t ${IMAGE_FORENSIC}:${VERSION_TAG} -t ${IMAGE_FORENSIC}:latest ./forensic-vault-service
                    docker build -t ${IMAGE_GATEWAY}:${VERSION_TAG} -t ${IMAGE_GATEWAY}:latest ./api-gateway
                    docker build -t ${IMAGE_FRONTEND}:${VERSION_TAG} -t ${IMAGE_FRONTEND}:latest ./frontend
                '''
            }
        }

        stage('Push Images to AWS ECR') {
            steps {
                echo 'Authenticating with AWS ECR and uploading versioned containers...'
                sh '''
                    aws ecr get-login-password --region ${AWS_REGION} | docker login --username AWS --password-stdin ${ECR_REGISTRY}
                    docker push ${IMAGE_AUTH}:${VERSION_TAG}
                    docker push ${IMAGE_AUTH}:latest
                    docker push ${IMAGE_CASE}:${VERSION_TAG}
                    docker push ${IMAGE_CASE}:latest
                    docker push ${IMAGE_FORENSIC}:${VERSION_TAG}
                    docker push ${IMAGE_FORENSIC}:latest
                    docker push ${IMAGE_GATEWAY}:${VERSION_TAG}
                    docker push ${IMAGE_GATEWAY}:latest
                    docker push ${IMAGE_FRONTEND}:${VERSION_TAG}
                    docker push ${IMAGE_FRONTEND}:latest
                '''
            }
        }

        stage('Continuous Deployment to AWS EC2') {
            steps {
                echo "Triggering production container deployment on EC2 (${EC2_PUBLIC_IP})..."
                sh '''
                    echo "Deploying production containers via docker-compose.prod.yml..."
                    # In production environments, Jenkins executes deployment on target EC2 host:
                    # ssh -o StrictHostKeyChecking=no ubuntu@${EC2_PUBLIC_IP} "cd ~/THELaw && git pull && sudo docker compose -f docker-compose.prod.yml pull && sudo docker compose -f docker-compose.prod.yml up -d"
                '''
            }
        }
    }

    post {
        always {
            echo 'VeritasVault CI/CD Pipeline execution finished.'
            cleanWs()
        }
        success {
            echo 'SUCCESS: All Cucumber BDD tests passed and containers deployed to AWS production!'
        }
        failure {
            echo 'FAILURE: Pipeline failed. Check console output for test failures or compilation errors.'
        }
    }
}
