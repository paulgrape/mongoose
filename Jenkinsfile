pipeline {
	agent any

	stages {
		stage('build') {
			steps {
				withGradle(gradle : 'Gradle 4.9') {
					sh 'gradle build' 
				}
			}
		}
	}
}