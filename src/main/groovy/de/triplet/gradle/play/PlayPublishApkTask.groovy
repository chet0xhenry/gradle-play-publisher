package de.triplet.gradle.play

import com.android.build.gradle.api.ApkVariantOutput
import com.google.api.client.http.FileContent
import com.google.api.services.androidpublisher.model.Apk
import com.google.api.services.androidpublisher.model.ApkListing
import com.google.api.services.androidpublisher.model.Track
import org.gradle.api.tasks.TaskAction

class PlayPublishApkTask extends PlayPublishTask {

    static def MAX_CHARACTER_LENGTH_FOR_WHATS_NEW_TEXT = 500
    static def FILE_NAME_FOR_WHATS_NEW_TEXT = "whatsnew"

    File inputFolder

    @TaskAction
    publishApks() {
        super.publish()

        List<Integer> versionCodes = new ArrayList<Integer>()

        variant.outputs
            .findAll { variantOutput -> variantOutput instanceof ApkVariantOutput }
            .each { variantOutput -> versionCodes.add(publishApk(new FileContent(AndroidPublisherHelper.MIME_TYPE_APK, variantOutput.outputFile)).getVersionCode())}

        Track track = new Track().setVersionCodes(versionCodes)
        edits.tracks()
                .update(variant.applicationId, editId, extension.track, track)
                .execute()

        edits.commit(variant.applicationId, editId).execute()
    }

    def Apk publishApk(apkFile) {

        Apk apk = edits.apks()
                .upload(variant.applicationId, editId, apkFile)
                .execute()

        Track newTrack = new Track().setVersionCodes([apk.getVersionCode()])
        if (extension.track?.equals("rollout")) {
            newTrack.setUserFraction(extension.userFraction)
        }
        edits.tracks()
                .update(variant.applicationId, editId, extension.track, newTrack)
                .execute()

        if (inputFolder.exists()) {

            // Matches if locale have the correct naming e.g. en-US for play store
            inputFolder.eachDirMatch(matcher) { dir ->
                File whatsNewFile = new File(dir, FILE_NAME_FOR_WHATS_NEW_TEXT + "-" + extension.track)

                if (!whatsNewFile.exists()) {
                    whatsNewFile = new File(dir, FILE_NAME_FOR_WHATS_NEW_TEXT)
                }

                if (whatsNewFile.exists()) {

                    def whatsNewText = TaskHelper.readAndTrimFile(whatsNewFile, MAX_CHARACTER_LENGTH_FOR_WHATS_NEW_TEXT, extension.errorOnSizeLimit)
                    def locale = dir.name

                    ApkListing newApkListing = new ApkListing().setRecentChanges(whatsNewText)
                    edits.apklistings()
                            .update(variant.applicationId, editId, apk.getVersionCode(), locale, newApkListing)
                            .execute()
                }
            }
        }

        return apk
    }

}
