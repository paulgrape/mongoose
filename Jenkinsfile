pipeline {
	agent any

	stages {
		stage('Build') {
			steps {
				sh "./gradlew build"
				//sh "mkdir -p output"
				//writeFile file: "output/usefulfile.txt", text: "This file is useful, need to archive it."				
			}
		}
		stage('Unit tests') {
			steps {
				sh "./gradlew :tests:unit:test"
				//sh "archiveArtifacts "${WORKSPACE}"
				//sh "mkdir -p output"
				//writeFile file: "output/usefulfile.txt", text: "This file is useful, need to archive it."				
			}
		}
	}
	post {
		success {
			archiveArtifacts "*"
		}
	}
}