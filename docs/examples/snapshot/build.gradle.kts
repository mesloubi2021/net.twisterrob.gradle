plugins {
	id("com.android.application") version "4.2.2"
	id("net.twisterrob.quality") version "0.14-SNAPSHOT"
}

android {
	compileSdkVersion(30)
	defaultConfig {
		minSdkVersion(14)
		targetSdkVersion(30)

		applicationId = "net.twisterrob.quality.examples.snapshot"
		versionCode = 1
		versionName = "1.0"
	}
}

repositories {
	google()
	mavenCentral()
}
