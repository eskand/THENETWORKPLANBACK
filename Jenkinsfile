// ============================================================
//  THENETWORKPLANBACK — pipeline Jenkins (API Spring Boot 4 / Java 21)
//
//  Étapes :
//    1. Compilation        mvnw clean test-compile (Maven vient du wrapper)
//    2. Base de test       PostgreSQL 18 jetable dans Docker, port libre
//    3. Tests              mvnw verify : tests unitaires + contextLoads
//                          (Flyway V1 → dernière, validation Hibernate),
//                          couverture JaCoCo, jar produit
//    4. Analyse SonarQube  mvn sonar:sonar, puis attente du Quality Gate
//    5. Image Docker       docker build (Dockerfile : jar découpé en couches)
//    6. Publication        docker push vers le registre — sur main seulement
//    7. Déploiement        kubectl set image sur le Deployment — sur main,
//                          si CI_DEPLOY=true sur le contrôleur
//
//  Ce qu'il attend de Jenkins :
//    - outil JDK « jdk21 » (Administrer Jenkins → Tools) ;
//    - serveur SonarQube « sonarqube » (Administrer Jenkins → System →
//      SonarQube servers) avec son jeton, et le webhook SonarQube → Jenkins
//      pour waitForQualityGate ;
//    - identifiant « registry-credentials » (utilisateur / mot de passe ou
//      jeton du registre d'images) ;
//    - identifiant « kubeconfig-netplus » (fichier kubeconfig) si CI_DEPLOY ;
//    - docker et kubectl sur l'agent.
//
//  Variables posées sur le contrôleur (facultatives) :
//    CI_REGISTRY   registre des images, défaut ghcr.io/eskand
//    CI_PG_HOST    hôte du PostgreSQL jetable vu de l'agent (voir ci-dessous)
//    CI_DEPLOY     « true » pour déployer main sur le cluster
//
//  Il tourne sur un agent Linux ou Windows : chaque commande passe par
//  `run`, qui choisit `sh` ou `bat`. Ces fonctions ne sont pas des étapes
//  déclaratives, d'où les blocs script { } qui les entourent.
//
//  Si Jenkins tourne lui-même dans un conteneur (jenkins/docker-compose.yml
//  du dépôt parent), le PostgreSQL jetable est un conteneur voisin : il
//  n'est pas joignable sur « localhost » mais sur « host.docker.internal ».
//  C'est CI_PG_HOST qui le dit.
//
//  Voir docs/50_JENKINS.md et docs/60_DOCKER_KUBERNETES.md du dépôt parent.
// ============================================================

def run(String cmd)       { isUnix() ? sh(cmd) : bat(cmd) }
def runStatus(String cmd) { isUnix() ? sh(script: cmd, returnStatus: true) : bat(script: cmd, returnStatus: true) }
def runOut(String cmd)    { (isUnix() ? sh(script: cmd, returnStdout: true) : bat(script: '@' + cmd, returnStdout: true)).trim() }
def mvn(String args)      { run((isUnix() ? './mvnw' : 'mvnw.cmd') + ' -B -ntp ' + args) }
def dockerTag(String s)   { s.replaceAll(/[^A-Za-z0-9_.-]/, '-').take(128) }

pipeline {
    agent any

    tools {
        jdk 'jdk21'
    }

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20'))
        timeout(time: 45, unit: 'MINUTES')
    }

    environment {
        REGISTRY   = "${env.CI_REGISTRY ?: 'ghcr.io/eskand'}"
        IMAGE      = "${REGISTRY}/netplus-back"
        K8S_NS     = 'netplus'
        K8S_DEPLOY = 'netplus-back'

        // Hôte auquel les tests joignent le PostgreSQL jetable (voir en-tête).
        PG_HOST = "${env.CI_PG_HOST ?: 'localhost'}"
        // Identifiants du PostgreSQL jetable : ce sont ceux que
        // application.properties lit (${DB_USERNAME}, ${DB_PASSWORD}).
        // Ce ne sont pas des secrets, le conteneur vit le temps du build.
        DB_USERNAME = 'postgres'
        DB_PASSWORD = 'ci'
        // Pas de Redis en CI : le cache retombe sur son niveau L1 en mémoire
        // (TwoLevelCache), l'application et les tests fonctionnent sans lui.
    }

    stages {

        stage('Compilation') {
            steps {
                script {
                    // Étiquettes de l'image : le commit court et la branche.
                    env.SHORT_SHA  = (env.GIT_COMMIT ?: runOut('git rev-parse HEAD')).take(7)
                    env.BRANCH_TAG = dockerTag(env.BRANCH_NAME ?: 'local')
                    mvn 'clean test-compile'
                }
            }
        }

        stage('Base de test') {
            steps {
                script {
                    // Un nom de conteneur par build, sans caractère refusé par Docker
                    // (BUILD_TAG = jenkins-<job>-<n>, avec des « %2F » en multibranche).
                    env.PG_CONTAINER = 'netplus-ci-pg-' + dockerTag(env.BUILD_TAG)

                    runStatus "docker rm -f ${env.PG_CONTAINER}"
                    // -P : port hôte choisi par Docker, deux builds peuvent cohabiter.
                    run "docker run -d --name ${env.PG_CONTAINER} -P " +
                        "-e POSTGRES_DB=networkplan -e POSTGRES_USER=${env.DB_USERNAME} " +
                        "-e POSTGRES_PASSWORD=${env.DB_PASSWORD} " +
                        "-e POSTGRES_INITDB_ARGS=--encoding=UTF8 postgres:18-alpine"

                    // « 0.0.0.0:32771 » (et parfois une ligne IPv6) → 32771
                    def portLine = runOut("docker port ${env.PG_CONTAINER} 5432/tcp").readLines()[0].trim()
                    env.PG_PORT = portLine.tokenize(':').last()
                    env.SPRING_DATASOURCE_URL = "jdbc:postgresql://${env.PG_HOST}:${env.PG_PORT}/networkplan"

                    // L'entrypoint de l'image démarre un serveur temporaire sans TCP
                    // pendant l'initialisation : -h localhost force le TCP, donc on
                    // n'obtient « ready » qu'une fois le vrai serveur en écoute.
                    timeout(time: 2, unit: 'MINUTES') {
                        waitUntil(initialRecurrencePeriod: 2000) {
                            runStatus("docker exec ${env.PG_CONTAINER} pg_isready -h localhost -U ${env.DB_USERNAME} -d networkplan") == 0
                        }
                    }
                    echo "PostgreSQL jetable prêt : ${env.SPRING_DATASOURCE_URL}"
                }
            }
        }

        stage('Tests') {
            steps {
                script {
                    // verify = tests (agent JaCoCo posé) + rapport jacoco.xml + package.
                    mvn 'verify'
                }
            }
            post {
                always {
                    junit testResults: 'target/surefire-reports/*.xml', allowEmptyResults: true
                }
            }
        }

        stage('Analyse SonarQube') {
            steps {
                script {
                    // withSonarQubeEnv fournit SONAR_HOST_URL et le jeton au plugin Maven ;
                    // projectKey, exclusions et chemin JaCoCo sont dans le pom.
                    withSonarQubeEnv('sonarqube') {
                        mvn "sonar:sonar -Dsonar.projectVersion=${env.SHORT_SHA}"
                    }
                }
            }
        }

        stage('Quality Gate') {
            steps {
                // Le verdict arrive par le webhook SonarQube → Jenkins.
                timeout(time: 10, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        stage('Image Docker') {
            steps {
                script {
                    run "docker build --pull -t ${env.IMAGE}:${env.SHORT_SHA} -t ${env.IMAGE}:${env.BRANCH_TAG} ."
                }
                archiveArtifacts artifacts: 'target/*.jar', excludes: 'target/*.jar.original', fingerprint: true
            }
        }

        stage('Publication') {
            when { branch 'main' }
            steps {
                script {
                    withCredentials([usernamePassword(credentialsId: 'registry-credentials',
                                                      usernameVariable: 'REG_USER',
                                                      passwordVariable: 'REG_PASS')]) {
                        // Chaînes en simples quotes : c'est le shell qui lit le secret, pas Groovy.
                        run(isUnix()
                            ? 'echo "$REG_PASS" | docker login -u "$REG_USER" --password-stdin ' + env.REGISTRY
                            : 'echo %REG_PASS%| docker login -u %REG_USER% --password-stdin ' + env.REGISTRY)
                    }
                    run "docker push ${env.IMAGE}:${env.SHORT_SHA}"
                    run "docker push ${env.IMAGE}:${env.BRANCH_TAG}"
                }
            }
        }

        stage('Déploiement') {
            when {
                allOf {
                    branch 'main'
                    environment name: 'CI_DEPLOY', value: 'true'
                }
            }
            steps {
                script {
                    // Les manifestes (k8s/ du dépôt parent) sont appliqués à part ;
                    // le pipeline ne fait que faire tourner l'image du Deployment.
                    withKubeConfig([credentialsId: 'kubeconfig-netplus']) {
                        run "kubectl -n ${env.K8S_NS} set image deployment/${env.K8S_DEPLOY} ${env.K8S_DEPLOY}=${env.IMAGE}:${env.SHORT_SHA}"
                        run "kubectl -n ${env.K8S_NS} rollout status deployment/${env.K8S_DEPLOY} --timeout=5m"
                    }
                }
            }
        }
    }

    post {
        always {
            script {
                if (env.PG_CONTAINER) {
                    runStatus "docker rm -f ${env.PG_CONTAINER}"
                }
                if (env.SHORT_SHA) {
                    // Les images locales ne servent plus une fois poussées (ou non).
                    runStatus "docker image rm -f ${env.IMAGE}:${env.SHORT_SHA} ${env.IMAGE}:${env.BRANCH_TAG}"
                }
                runStatus 'docker logout ' + env.REGISTRY
            }
        }
    }
}
