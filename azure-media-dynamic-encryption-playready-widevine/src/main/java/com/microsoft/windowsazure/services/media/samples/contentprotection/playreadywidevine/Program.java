package com.microsoft.windowsazure.services.media.samples.contentprotection.playreadywidevine;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.windowsazure.Configuration;
import com.microsoft.windowsazure.core.utils.Base64;
import com.microsoft.windowsazure.exception.ServiceException;
import com.microsoft.windowsazure.services.media.*;
import com.microsoft.windowsazure.services.media.authentication.AzureAdClientSymmetricKey;
import com.microsoft.windowsazure.services.media.authentication.AzureAdTokenCredentials;
import com.microsoft.windowsazure.services.media.authentication.AzureAdTokenProvider;
import com.microsoft.windowsazure.services.media.authentication.AzureEnvironments;
import com.microsoft.windowsazure.services.media.implementation.templates.playreadylicense.*;
import com.microsoft.windowsazure.services.media.implementation.templates.tokenrestriction.*;
import com.microsoft.windowsazure.services.media.implementation.templates.widevine.*;
import com.microsoft.windowsazure.services.media.models.*;
import com.microsoft.windowsazure.services.media.models.ContentKeyAuthorizationPolicyRestriction.ContentKeyRestrictionType;

import javax.xml.bind.JAXBException;
import java.io.*;
import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class Program {

    private static MediaContract mediaService;

    // Media Services account credentials configuration
    private static String tenant = "tenant.domain.com";
    private static String clientId = "<client id>";
    private static String clientKey = "<client key>";
    private static String restApiEndpoint = "https://accountname.restv2.regionname.media.azure.net/api/";

    // Encoder configuration
    private static String preferedEncoder = "Media Encoder Standard";
    private static String encodingPreset = "H264 Multiple Bitrate 720p";

    // Content Key Authorization Policy Token Restriction configuration
    private static boolean tokenRestriction = true; // true: use token restriction policy;
                                                    // false: use open
    private static TokenType tokenType = TokenType.JWT;

    // Utility classes should not have a public or default constructor
    private Program() {
    }

    public static void main(String[] args) {
        ExecutorService executorService = Executors.newFixedThreadPool(1);

        try {
            // Setup Azure AD Service Principal Symmetric Key Credentials
            AzureAdTokenCredentials credentials = new AzureAdTokenCredentials(
                    tenant,
                    new AzureAdClientSymmetricKey(clientId, clientKey),
                    AzureEnvironments.AzureCloudEnvironment);

            AzureAdTokenProvider provider = new AzureAdTokenProvider(credentials, executorService);

            // create a new configuration with the new credentials
            Configuration configuration = MediaConfiguration.configureWithAzureAdTokenProvider(
                    new URI(restApiEndpoint),
                    provider);

            // create the media service provisioned with the new configuration
            mediaService = MediaService.create(configuration);

            System.out.println("Azure SDK for Java - PlayReady & Widevine Dynamic Encryption Sample");

            // Upload a local file to a media asset.
            AssetInfo uploadAsset = uploadFileAndCreateAsset("Azure-Video.wmv");
            System.out.println("Uploaded Asset Id: " + uploadAsset.getId());

            // Transform the asset.
            AssetInfo encodedAsset = encode(uploadAsset);
            System.out.println("Encoded Asset Id: " + encodedAsset.getId());

            // Create the ContentKey
            ContentKeyInfo contentKeyInfo = createCommonTypeContentKey(encodedAsset);
            System.out.println("Common Encryption Content Key: " + contentKeyInfo.getId());

            // Create the ContentKeyAuthorizationPolicy
            String tokenTemplateString = null;
            if (tokenRestriction) {
                tokenTemplateString = addTokenRestrictedAuthorizationPolicy(contentKeyInfo, tokenType);
            } else {
                addOpenAuthorizationPolicy(contentKeyInfo);
            }

            // Create the AssetDeliveryPolicy
            createAssetDeliveryPolicy(encodedAsset, contentKeyInfo);

            if (tokenTemplateString != null) {
                // Deserializes a string containing the XML representation of the TokenRestrictionTemplate
                TokenRestrictionTemplate tokenTemplate = TokenRestrictionTemplateSerializer
                        .deserialize(tokenTemplateString);

                // Generate a test token based on the the data in the given
                // TokenRestrictionTemplate.
                // Note: You need to pass the key id Guid because we specified
                // TokenClaim.ContentKeyIdentifierClaim in during the creation
                // of TokenRestrictionTemplate.
                UUID rawKey = UUID.fromString(contentKeyInfo.getId().substring("nb:kid:UUID:".length()));

                // Token expiration: 1-year
                Calendar date = Calendar.getInstance();
                date.setTime(new Date());
                date.add(Calendar.YEAR, 1);

                // Generate token
                String testToken = TokenRestrictionTemplateSerializer.generateTestToken(tokenTemplate, null, rawKey,
                        date.getTime(), null);

                System.out.println(tokenTemplate.getTokenType().toString() + " Test Token: Bearer " + testToken);
            }

            // Create the Streaming Origin Locator
            String url = getStreamingOriginLocator(encodedAsset);

            System.out.println("Origin Locator Url: " + url);
            System.out.println("Sample completed!");

        } catch (ServiceException se) {
            System.out.println("ServiceException encountered.");
            System.out.println(se.toString());
        } catch (Exception e) {
            System.out.println("Exception encountered.");
            System.out.println(e.toString());
        } finally {
            executorService.shutdown();
        }
    }

    // Upload a media file to your Media Services account.
    // This code creates an Asset, an AccessPolicy (using Write access) and a
    // Locator, and uses those objects to upload a local file.
    private static AssetInfo uploadFileAndCreateAsset(String fileName)
            throws ServiceException, FileNotFoundException, NoSuchAlgorithmException {
        WritableBlobContainerContract uploader;
        AssetInfo resultAsset;
        AccessPolicyInfo uploadAccessPolicy;
        LocatorInfo uploadLocator = null;

        // Create an Asset
        resultAsset = mediaService.create(Asset.create().setName(fileName).setAlternateId("altId"));
        System.out.println("Created Asset " + fileName);

        // Create an AccessPolicy that provides Write access for 15 minutes
        uploadAccessPolicy = mediaService
                .create(AccessPolicy.create("uploadAccessPolicy", 15.0, EnumSet.of(AccessPolicyPermission.WRITE)));

        // Create a Locator using the AccessPolicy and Asset
        uploadLocator = mediaService
                .create(Locator.create(uploadAccessPolicy.getId(), resultAsset.getId(), LocatorType.SAS));

        // Create the Blob Writer using the Locator
        uploader = mediaService.createBlobWriter(uploadLocator);

        // The local file that will be uploaded to your Media Services account
        InputStream input = new FileInputStream(
                new File(Program.class.getClassLoader().getResource("").getPath() + fileName));

        System.out.println("Uploading " + fileName);

        // Upload the local file to the asset
        uploader.createBlockBlob(fileName, input);

        // Inform Media Services about the uploaded files
        mediaService.action(AssetFile.createFileInfos(resultAsset.getId()));
        System.out.println("Uploaded Asset File " + fileName);

        mediaService.delete(Locator.delete(uploadLocator.getId()));
        mediaService.delete(AccessPolicy.delete(uploadAccessPolicy.getId()));

        return resultAsset;
    }

    // Create a Job that contains a Task to transform the Asset
    private static AssetInfo encode(AssetInfo assetToEncode)
            throws ServiceException, InterruptedException {
        // Retrieve the list of Media Processors that match the name
        ListResult<MediaProcessorInfo> mediaProcessors = mediaService
                .list(MediaProcessor.list().set("$filter", String.format("Name eq '%s'", preferedEncoder)));

        // Use the latest version of the Media Processor
        MediaProcessorInfo mediaProcessor = null;
        for (MediaProcessorInfo info : mediaProcessors) {
            if (null == mediaProcessor || info.getVersion().compareTo(mediaProcessor.getVersion()) > 0) {
                mediaProcessor = info;
            }
        }

        System.out.println("Using Media Processor: " + mediaProcessor.getName() + " " + mediaProcessor.getVersion());

        // Create a task with the specified Media Processor
        String outputAssetName = String.format("%s as %s", assetToEncode.getName(), encodingPreset);
        String taskXml = "<taskBody><inputAsset>JobInputAsset(0)</inputAsset>"
                + "<outputAsset assetCreationOptions=\"0\"" // AssetCreationOptions.None
                + " assetName=\"" + outputAssetName + "\">JobOutputAsset(0)</outputAsset></taskBody>";

        Task.CreateBatchOperation task = Task.create(mediaProcessor.getId(), taskXml)
                .setConfiguration(encodingPreset).setName("Encoding");

        /// Create the Job; this automatically schedules and runs it.
        Job.Creator jobCreator = Job.create()
                .setName(String.format("Encoding %s to %s", assetToEncode.getName(), encodingPreset))
                .addInputMediaAsset(assetToEncode.getId()).setPriority(2).addTaskCreator(task);
        JobInfo job = mediaService.create(jobCreator);

        String jobId = job.getId();
        System.out.println("Created Job with Id: " + jobId);

        // Check to see if the Job has completed
        checkJobStatus(jobId);
        // Done with the Job

        // Retrieve the output Asset
        ListResult<AssetInfo> outputAssets = mediaService.list(Asset.list(job.getOutputAssetsLink()));
        return outputAssets.get(0);
    }

    public static ContentKeyInfo createCommonTypeContentKey(AssetInfo asset) {
        try {
            // Get the protection key id for ContentKey
            String protectionKeyId = mediaService
                    .action(ProtectionKey.getProtectionKeyId(ContentKeyType.CommonEncryption));

            // Download and create the X509 certificate
            String protectionKey = mediaService.action(ProtectionKey.getProtectionKey(protectionKeyId));
            X509Certificate certificate = (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(Base64.decode(protectionKey)));

            // Create a new ContentKey (secure random)
            byte[] contentKeyData = new byte[16];
            EncryptionUtils.eraseKey(contentKeyData);

            // Encrypt ContentKey
            byte[] encryptedContentKey = EncryptionUtils.encryptSymmetricKeyData(certificate, contentKeyData);
            String encryptedContentKeyString = Base64.encode(encryptedContentKey);

            // Create the ContentKey Id
            UUID contentKeyIdUuid = UUID.randomUUID();
            String contentKeyId = String.format("nb:kid:UUID:%s", contentKeyIdUuid.toString());

            // Calculate the checksum
            String checksum = EncryptionUtils.calculateChecksum(contentKeyData, contentKeyIdUuid);

            // Create the ContentKey entity
            ContentKeyInfo contentKey = mediaService.create(
            		ContentKey.create(contentKeyId, ContentKeyType.CommonEncryption, encryptedContentKeyString)
                            .setChecksum(checksum)
                            .setProtectionKeyType(ProtectionKeyType.X509CertificateThumbprint)
                            .setName("Common Encryption Content Key")
                            .setProtectionKeyId(EncryptionUtils.getThumbPrint(certificate)));

            // Associate the ContentKey with the Asset
            mediaService.action(Asset.linkContentKey(asset.getId(), contentKeyId));

            return contentKey;

        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

        return null;
    }

    public static void addOpenAuthorizationPolicy(ContentKeyInfo contentKey) throws Exception {
        // Create ContentKeyAuthorizationPolicyRestriction (Open)
        List<ContentKeyAuthorizationPolicyRestriction> restrictions = new ArrayList<ContentKeyAuthorizationPolicyRestriction>();
        restrictions.add(new ContentKeyAuthorizationPolicyRestriction("Open Restriction",
        		ContentKeyRestrictionType.Open.getValue(), null));

        // Create ContentKeyAuthorizationPolicyOption (PlayReady)
        String playReadyLicenseTemplate = configurePlayReadyLicenceTemplate();
        ContentKeyAuthorizationPolicyOptionInfo playReadyOption = mediaService
                .create(ContentKeyAuthorizationPolicyOption.create("PlayReady Option",
                        ContentKeyDeliveryType.PlayReadyLicense.getCode(), playReadyLicenseTemplate, restrictions));

        // Create ContentKeyAuthorizationPolicyOption (Widevine)
        String widevineLicenseTemplate = configureWidevineLicenceTemplate();
        ContentKeyAuthorizationPolicyOptionInfo widevineOption = mediaService
                .create(ContentKeyAuthorizationPolicyOption.create("Widevine Option",
                        ContentKeyDeliveryType.Widevine.getCode(), widevineLicenseTemplate, restrictions));

        // Create ContentKeyAuthorizationPolicy
        ContentKeyAuthorizationPolicyInfo contentKeyAuthorizationPolicy = mediaService
                .create(ContentKeyAuthorizationPolicy.create("PlayReady Open Content Key Authorization Policy"));

        // Link the ContentKeyAuthorizationPolicyOptions to the ContentKeyAuthorizationPolicy
        mediaService.action(ContentKeyAuthorizationPolicy.linkOptions(contentKeyAuthorizationPolicy.getId(), playReadyOption.getId()));
        mediaService.action(ContentKeyAuthorizationPolicy.linkOptions(contentKeyAuthorizationPolicy.getId(), widevineOption.getId()));

        // Associate the ContentKeyAuthorizationPolicy with the ContentKey
        mediaService.update(ContentKey.update(contentKey.getId(), contentKeyAuthorizationPolicy.getId()));

        System.out.println("Added Content Key Authorization Policy: " + contentKeyAuthorizationPolicy.getName());
    }

    public static String addTokenRestrictedAuthorizationPolicy(ContentKeyInfo contentKey, TokenType tokenType)
            throws Exception {
        // Create ContentKeyAuthorizationPolicyRestriction (Token)
        String tokenRestrictionString = generateTokenRequirements(tokenType);
        List<ContentKeyAuthorizationPolicyRestriction> restrictions = new ArrayList<ContentKeyAuthorizationPolicyRestriction>();
        restrictions.add(new ContentKeyAuthorizationPolicyRestriction("Token Restriction",
                ContentKeyRestrictionType.TokenRestricted.getValue(), tokenRestrictionString));

        // Create ContentKeyAuthorizationPolicyOptions (PlayReady)
        String playReadyLicenseTemplateString = configurePlayReadyLicenceTemplate();
        ContentKeyAuthorizationPolicyOptionInfo playReadyOption = mediaService
                .create(ContentKeyAuthorizationPolicyOption.create("PlayReady Option",
                        ContentKeyDeliveryType.PlayReadyLicense.getCode(), playReadyLicenseTemplateString, restrictions));

        // Create ContentKeyAuthorizationPolicyOption (Widevine)
        String widevineLicenseTemplate = configureWidevineLicenceTemplate();
        ContentKeyAuthorizationPolicyOptionInfo widevineOption = mediaService
                .create(ContentKeyAuthorizationPolicyOption.create("Widevine Option",
                        ContentKeyDeliveryType.Widevine.getCode(), widevineLicenseTemplate, restrictions));

        // Create ContentKeyAuthorizationPolicy
        ContentKeyAuthorizationPolicyInfo contentKeyAuthorizationPolicy = mediaService
                .create(ContentKeyAuthorizationPolicy.create("PlayReady Token Content Key Authorization Policy"));

        // Link the ContentKeyAuthorizationPolicyOptions to the ContentKeyAuthorizationPolicy
        mediaService.action(ContentKeyAuthorizationPolicy.linkOptions(contentKeyAuthorizationPolicy.getId(), playReadyOption.getId()));
        mediaService.action(ContentKeyAuthorizationPolicy.linkOptions(contentKeyAuthorizationPolicy.getId(), widevineOption.getId()));

        // Associate the ContentKeyAuthorizationPolicy with the ContentKey
        mediaService.update(ContentKey.update(contentKey.getId(), contentKeyAuthorizationPolicy.getId()));

        System.out.println("Added Content Key Authorization Policy: " + contentKeyAuthorizationPolicy.getName());

        return tokenRestrictionString;
    }

    public static void createAssetDeliveryPolicy(AssetInfo asset, ContentKeyInfo key) throws ServiceException {
        String acquisitionUrl = mediaService
                .create(ContentKey.getKeyDeliveryUrl(key.getId(), ContentKeyDeliveryType.PlayReadyLicense));
        String widevineUrl = mediaService
                .create(ContentKey.getKeyDeliveryUrl(key.getId(), ContentKeyDeliveryType.Widevine));

        Map<AssetDeliveryPolicyConfigurationKey, String> assetDeliveryPolicyConfiguration
            = new HashMap<AssetDeliveryPolicyConfigurationKey, String>();

        if (widevineUrl.contains("?")) {
            widevineUrl = widevineUrl.substring(0, widevineUrl.indexOf("?"));
        }

        assetDeliveryPolicyConfiguration.put(AssetDeliveryPolicyConfigurationKey.PlayReadyLicenseAcquisitionUrl,
                acquisitionUrl);
        assetDeliveryPolicyConfiguration.put(AssetDeliveryPolicyConfigurationKey.WidevineBaseLicenseAcquisitionUrl,
                widevineUrl);

        AssetDeliveryPolicyInfo assetDeliveryPolicy = mediaService.create(AssetDeliveryPolicy.create()
                .setName("PlayReady & Widevine Dash Asset Delivery Policy")
                .setAssetDeliveryConfiguration(assetDeliveryPolicyConfiguration)
                .setAssetDeliveryPolicyType(AssetDeliveryPolicyType.DynamicCommonEncryption)
                .setAssetDeliveryProtocol(EnumSet.of(AssetDeliveryProtocol.Dash)));

        // Link the AssetDeliveryPolicy to the Asset
        mediaService.action(Asset.linkDeliveryPolicy(asset.getId(), assetDeliveryPolicy.getId()));

        System.out.println("Added Asset Delivery Policy: " + assetDeliveryPolicy.getName());
    }

    public static String getStreamingOriginLocator(AssetInfo asset) throws ServiceException {
        // Get the .ISM AssetFile
        ListResult<AssetFileInfo> assetFiles = mediaService.list(AssetFile.list(asset.getAssetFilesLink()));
        AssetFileInfo streamingAssetFile = null;
        for (AssetFileInfo file : assetFiles) {
            if (file.getName().toLowerCase().endsWith(".ism")) {
                streamingAssetFile = file;
                break;
            }
        }

        AccessPolicyInfo originAccessPolicy;
        LocatorInfo originLocator = null;

        // Create a 30-day readonly AccessPolicy
        double durationInMinutes = 60 * 24 * 30;
        originAccessPolicy = mediaService.create(
                AccessPolicy.create("Streaming policy", durationInMinutes, EnumSet.of(AccessPolicyPermission.READ)));

        // Create a Locator using the AccessPolicy and Asset
        originLocator = mediaService
                .create(Locator.create(originAccessPolicy.getId(), asset.getId(), LocatorType.OnDemandOrigin));

        // Create a Smooth Streaming base URL
        return originLocator.getPath() + streamingAssetFile.getName() + "/manifest";
    }

    private static void checkJobStatus(String jobId) throws InterruptedException, ServiceException {
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
    }

    private static String configurePlayReadyLicenceTemplate() throws JAXBException {
        // Configure PlayReady License Template and serialize it to XML
        PlayReadyLicenseResponseTemplate responseTemplate = new PlayReadyLicenseResponseTemplate();
        PlayReadyLicenseTemplate licenseTemplate = new PlayReadyLicenseTemplate();
        responseTemplate.getLicenseTemplates().add(licenseTemplate);
        PlayReadyPlayRight playRight = new PlayReadyPlayRight();
        licenseTemplate.setPlayRight(playRight);
        licenseTemplate.setContentKey(new ContentEncryptionKeyFromHeader());

        return MediaServicesLicenseTemplateSerializer.serialize(responseTemplate);
    }

    private static String configureWidevineLicenceTemplate() throws JsonProcessingException {
        // Configure Widevine License Template and serialize it to JSON
        WidevineMessage message = new WidevineMessage();
        message.setAllowedTrackTypes(AllowedTrackTypes.SD_HD);
        ContentKeySpecs ckspecs = new ContentKeySpecs();
        message.setContentKeySpecs(new ContentKeySpecs[] { ckspecs });
        ckspecs.setRequiredOutputProtection(new RequiredOutputProtection());
        ckspecs.getRequiredOutputProtection().setHdcp(Hdcp.HDCP_NONE);
        ckspecs.setSecurityLevel(1);
        ckspecs.setTrackType("SD");
        message.setPolicyOverrides(new Object() {
              public boolean can_play = true;
              public boolean can_persist = true;
              public boolean can_renew  = false;
        });

        ObjectMapper mapper = new ObjectMapper();

        return mapper.writeValueAsString(message);
    }

    private static String generateTokenRequirements(TokenType tokenType) throws Exception {
        TokenRestrictionTemplate template = new TokenRestrictionTemplate(tokenType);

        template.setAudience(new URI("urn:contoso"));
        template.setIssuer(new URI("https://sts.contoso.com"));
        template.setPrimaryVerificationKey(new SymmetricVerificationKey());
        template.getRequiredClaims().add(TokenClaim.getContentKeyIdentifierClaim());

        return TokenRestrictionTemplateSerializer.serialize(template);
    }
}
