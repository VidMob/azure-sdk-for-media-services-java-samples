package com.microsoft.windowsazure.services.media.samples.analytics.indexer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.EnumSet;

import com.microsoft.windowsazure.Configuration;
import com.microsoft.windowsazure.exception.ServiceException;
import com.microsoft.windowsazure.services.media.MediaConfiguration;
import com.microsoft.windowsazure.services.media.MediaContract;
import com.microsoft.windowsazure.services.media.MediaService;
import com.microsoft.windowsazure.services.media.WritableBlobContainerContract;
import com.microsoft.windowsazure.services.media.models.AccessPolicy;
import com.microsoft.windowsazure.services.media.models.AccessPolicyInfo;
import com.microsoft.windowsazure.services.media.models.AccessPolicyPermission;
import com.microsoft.windowsazure.services.media.models.Asset;
import com.microsoft.windowsazure.services.media.models.AssetFile;
import com.microsoft.windowsazure.services.media.models.AssetFileInfo;
import com.microsoft.windowsazure.services.media.models.AssetInfo;
import com.microsoft.windowsazure.services.media.models.Job;
import com.microsoft.windowsazure.services.media.models.JobInfo;
import com.microsoft.windowsazure.services.media.models.JobState;
import com.microsoft.windowsazure.services.media.models.ListResult;
import com.microsoft.windowsazure.services.media.models.Locator;
import com.microsoft.windowsazure.services.media.models.LocatorInfo;
import com.microsoft.windowsazure.services.media.models.LocatorType;
import com.microsoft.windowsazure.services.media.models.MediaProcessor;
import com.microsoft.windowsazure.services.media.models.MediaProcessorInfo;
import com.microsoft.windowsazure.services.media.models.Task;

public final class Program {

    private static MediaContract mediaService;

    // Media Services account credentials configuration
    private static String mediaServiceUri = "https://media.windows.net/API/";
    private static String oAuthUri = "https://wamsprodglobal001acs.accesscontrol.windows.net/v2/OAuth2-13";
    private static String clientId = "%media-services-account-name%";
    private static String clientSecret = "%media-services-account-key%";
    private static String scope = "urn:WindowsAzureMediaServices";
    
    // Input file
    private static String mediaFileName = "Azure-Video.wmv";
    
    // Indexer processor configuration parameters
    private static String indexerProcessorName = "Azure Media Indexer";
    private static String indexerTaskPresetTemplateFileName = "indexerTaskPresetTemplate.xml";
    private static String title = "";
    private static String description = "";
    private static String language = "English";
    private static String captionFormats = "ttml;sami;webvtt";
    private static String generateAIB = "true";
    private static String generateKeywords = "true";
    
    // Destination path
    private static String destinationPath = "IndexerOutput";

    // Utility classes should not have a public or default constructor
    private Program() {
    }

    public static void main(String[] args) {
        try {
            // Set up the MediaContract object to call into the Media Services account
            Configuration configuration = MediaConfiguration.configureWithOAuthAuthentication(
                    mediaServiceUri, oAuthUri, clientId, clientSecret, scope);
            mediaService = MediaService.create(configuration);
            
            System.out.println("Azure SDK for Java - Media Analytics Sample (Indexer)");

            // Upload a local file to an Asset
            AssetInfo sourceAsset = uploadFileAndCreateAsset(mediaFileName);
            System.out.println("Uploaded Asset Id: " + sourceAsset.getId());

            // Create indexing task configuration based on parameters
            String indexerTaskPresetTemplate = new String(Files.readAllBytes(Paths.get(Program.class.getClassLoader().getResource("").getPath() + indexerTaskPresetTemplateFileName)));
            String taskConfiguration = String.format(indexerTaskPresetTemplate, title, description, language, captionFormats, generateAIB, generateKeywords);
            
            // Run indexing job to generate output asset
            AssetInfo outputAsset = runIndexingJob(sourceAsset, taskConfiguration);
            System.out.println("Encoded Asset Id: " + outputAsset.getId());

            // Download output asset files
            downloadAssetFiles(outputAsset, destinationPath);
            
        } catch (ServiceException se) {
            System.out.println("ServiceException encountered.");
            System.out.println(se.toString());
        } catch (Exception e) {
            System.out.println("Exception encountered.");
            System.out.println(e.toString());
        }
    }

    // Upload a media file to your Media Services account.
    // This code creates an Asset, an AccessPolicy (using Write access) and a
    // Locator, and uses those objects to upload a local file.
    private static AssetInfo uploadFileAndCreateAsset(String fileName)
            throws ServiceException, FileNotFoundException, NoSuchAlgorithmException {
        WritableBlobContainerContract uploader;
        AssetInfo asset;
        AccessPolicyInfo uploadAccessPolicy;
        LocatorInfo uploadLocator = null;

        // Create an empty Asset
        asset = mediaService.create(Asset.create().setName(String.format("Media File %s", fileName)));
        System.out.println("Asset created " + asset.getName());

        // Create an AccessPolicy that provides Write access for 15 minutes
        uploadAccessPolicy = mediaService
                .create(AccessPolicy.create("uploadAccessPolicy", 15.0, EnumSet.of(AccessPolicyPermission.WRITE)));

        // Create a SAS Locator using the AccessPolicy and Asset
        uploadLocator = mediaService
                .create(Locator.create(uploadAccessPolicy.getId(), asset.getId(), LocatorType.SAS));

        // Create the Blob Writer using the Locator
        uploader = mediaService.createBlobWriter(uploadLocator);

        // The local file that will be uploaded to your Media Services account
        InputStream input = new FileInputStream(
                new File(Program.class.getClassLoader().getResource("").getPath() + fileName));

        System.out.println("Uploading " + fileName);

        // Upload the local file to the asset
        uploader.createBlockBlob(fileName, input);

        // Inform Media Services about the uploaded files
        mediaService.action(AssetFile.createFileInfos(asset.getId()));
        System.out.println("Uploaded Asset File " + fileName);

        // Delete the SAS Locator (and Access Policy) for the Asset since we are done uploading files
        mediaService.delete(Locator.delete(uploadLocator.getId()));
        mediaService.delete(AccessPolicy.delete(uploadAccessPolicy.getId()));

        return asset;
    }

    // Create a Job that contains a Task to transform the Asset
    private static AssetInfo runIndexingJob(AssetInfo asset, String taskConfiguration)
            throws ServiceException, InterruptedException {
        // Retrieve the list of Media Processors that match the name
        ListResult<MediaProcessorInfo> mediaProcessors = mediaService
                .list(MediaProcessor.list().set("$filter", String.format("Name eq '%s'", indexerProcessorName)));

        // Use the latest version of the Media Processor
        MediaProcessorInfo mediaProcessor = null;
        for (MediaProcessorInfo info : mediaProcessors) {
            if (null == mediaProcessor || info.getVersion().compareTo(mediaProcessor.getVersion()) > 0) {
                mediaProcessor = info;
            }
        }

        System.out.println("Using Media Processor: " + mediaProcessor.getName() + " " + mediaProcessor.getVersion());

        // Create a task with the specified Media Processor
        String outputAssetName = String.format("Indexer Results %s", asset.getName());
        String taskXml = "<taskBody><inputAsset>JobInputAsset(0)</inputAsset>"
                + "<outputAsset assetCreationOptions=\"0\"" // AssetCreationOptions.None
                + " assetName=\"" + outputAssetName + "\">JobOutputAsset(0)</outputAsset></taskBody>";

        Task.CreateBatchOperation task = Task.create(mediaProcessor.getId(), taskXml)
                .setConfiguration(taskConfiguration).setName("Indexing Task");

        // Create the Job; this automatically schedules and runs it.
        Job.Creator jobCreator = Job.create()
                .setName("Indexing Job")
                .addInputMediaAsset(asset.getId()).setPriority(0).addTaskCreator(task);
        JobInfo job = mediaService.create(jobCreator);
        
        String jobId = job.getId();
        System.out.println("Created Job with Id: " + jobId);

        // Check to see if the Job has completed
        JobState result = checkJobStatus(jobId);
        // Done with the Job
        
        if (result != JobState.Finished) {
        	System.out.println("The job has finished with a wrong status: " + result.toString());
        	throw new RuntimeException();
        }

        System.out.println("Job Finished!");
        
        // Get the output Asset
        ListResult<AssetInfo> outputAssets = mediaService.list(Asset.list(job.getOutputAssetsLink()));
        AssetInfo outputAsset = outputAssets.get(0); 
        
        System.out.println("Output asset: " + outputAsset.getName());
        
        return outputAsset;
    }

    private static JobState checkJobStatus(String jobId) throws InterruptedException, ServiceException {
        boolean done = false;
        JobState jobState = null;
        while (!done) {
            // Sleep for 5 seconds
            Thread.sleep(5000);
            
            // Query the updated Job state
            jobState = mediaService.get(Job.get(jobId)).getState();
            System.out.println("Job state: " + jobState);

            if (jobState == JobState.Finished || jobState == JobState.Canceled || jobState == JobState.Error) {
                done = true;
            }
        }
        
        return jobState;
    }
    
    private static void downloadAssetFiles(AssetInfo asset, String destinationPath) throws ServiceException, IOException {
        // Create destination directory if does not exist
        new File(destinationPath).mkdir();

        // Create an AccessPolicy that provides Read access for 15 minutes
        AccessPolicyInfo accessPolicy = mediaService
                .create(AccessPolicy.create("downloadAccessPolicy", 15.0, EnumSet.of(AccessPolicyPermission.READ)));

        // Create a SAS Locator using the AccessPolicy and Asset
        LocatorInfo sasLocator = mediaService
                .create(Locator.create(accessPolicy.getId(), asset.getId(), LocatorType.SAS));

        // List all the Asset Files
        ListResult<AssetFileInfo> assetFiles = mediaService.list(AssetFile.list(asset.getAssetFilesLink()));
        
        for (AssetFileInfo file : assetFiles) {
            System.out.print("Downloading " + file.getName() + " output file...");

            URL downloadUrl = new URL(sasLocator.getBaseUri() + file.getName() + sasLocator.getContentAccessToken());
            HttpURLConnection httpConn = (HttpURLConnection) downloadUrl.openConnection();
            InputStream inputStream = httpConn.getInputStream();
            Files.copy(inputStream, Paths.get(destinationPath, file.getName()));
            inputStream.close();
        	
        	System.out.println("Done!");
        }

        // Clean up Locator and Access Policy
        mediaService.delete(Locator.delete(sasLocator.getId()));
        mediaService.delete(AccessPolicy.delete(accessPolicy.getId()));
    }
}