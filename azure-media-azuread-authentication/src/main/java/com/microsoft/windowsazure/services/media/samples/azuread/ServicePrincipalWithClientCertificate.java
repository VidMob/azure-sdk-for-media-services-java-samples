package com.microsoft.windowsazure.services.media.samples.azuread;

import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.security.PrivateKey;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.microsoft.aad.adal4j.AsymmetricKeyCredential;
import com.microsoft.windowsazure.Configuration;
import com.microsoft.windowsazure.exception.ServiceException;
import com.microsoft.windowsazure.services.media.MediaConfiguration;
import com.microsoft.windowsazure.services.media.MediaContract;
import com.microsoft.windowsazure.services.media.MediaService;
import com.microsoft.windowsazure.services.media.authentication.AzureAdClientSymmetricKey;
import com.microsoft.windowsazure.services.media.authentication.AzureAdClientUsernamePassword;
import com.microsoft.windowsazure.services.media.authentication.AzureAdTokenCredentials;
import com.microsoft.windowsazure.services.media.authentication.AzureAdTokenProvider;
import com.microsoft.windowsazure.services.media.authentication.AzureEnvironments;
import com.microsoft.windowsazure.services.media.models.Asset;
import com.microsoft.windowsazure.services.media.models.AssetInfo;
import com.microsoft.windowsazure.services.media.models.ListResult;

public final class ServicePrincipalWithClientCertificate {

    // Utility classes should not have a public or default constructor
    private ServicePrincipalWithClientCertificate() {
    }

    public static void main(String[] args) {
    	ExecutorService executorService = Executors.newFixedThreadPool(1);

        try {
            String tenant = "tenant.domain.com";
            String clientId = "%client_id%";
            String restApiEndpoint = "https://account.restv2.region.media.azure.net/api/";
            String pfxFilename = "%path_to_keystore.pfx%";
            String pfxPassword = "%keystore_password%";
            InputStream pfx = new FileInputStream(pfxFilename);

            // Connect to Media Services API with service principal and client certificate
            AzureAdTokenCredentials credentials = new AzureAdTokenCredentials(
                    tenant,
                    AsymmetricKeyCredential.create(clientId, pfx, pfxPassword),
                    AzureEnvironments.AzureCloudEnvironment);

            AzureAdTokenProvider provider = new AzureAdTokenProvider(credentials, executorService);

            // create a new configuration with the new credentials
            Configuration configuration = MediaConfiguration.configureWithAzureAdTokenProvider(
                    new URI(restApiEndpoint),
                    provider);

            // create the media service with the new configuration
            MediaContract mediaService = MediaService.create(configuration);

            System.out.println("Listing assets");

            ListResult<AssetInfo> assets = mediaService.list(Asset.list());

            for (AssetInfo asset : assets) {
                System.out.println(asset.getId());
            }

        } catch (ServiceException se) {
            System.out.println("ServiceException encountered.");
            System.out.println(se.toString());
        } catch (Throwable e) {
            System.out.println("Exception encountered.");
            e.printStackTrace();
            System.out.println(e.toString());
        } finally {
        	executorService.shutdown();
        }
    }
}