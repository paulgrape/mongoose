pipeline {
	agent any

	stages {
		//stage('one') {
			// Make the output directory.
		//	sh "mkdir -p output"

			// Write an useful file, which is needed to be archived.
		//	writeFile file: "output/usefulfile.txt", text: "This file is useful, need to archive it."
		//}
		stage('build') {
			steps {
				sh "gradle build"
				sh "gradle test"
				//sh "mkdir -p output"
				//writeFile file: "output/usefulfile.txt", text: "This file is useful, need to archive it."				
			}
		}
	}
}