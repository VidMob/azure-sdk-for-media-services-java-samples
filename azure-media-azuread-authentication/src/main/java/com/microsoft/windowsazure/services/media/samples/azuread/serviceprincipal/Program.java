package com.microsoft.windowsazure.services.media.samples.azuread.serviceprincipal;

import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.security.PrivateKey;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

public final class Program {

    // Utility classes should not have a public or default constructor
    private Program() {
    }
    
    public static void main(String[] args) {
    	
    	// uncomment line below to test username/password authentication
    	///clientAuth();
    	
    	// uncomment line below to test service principal with client id + client key authentication
    	servicePrincipalAuth();
    	
    	// uncomment line below to test service principal with client id + certificate authentication
    	///servicePrincipalCertificate()
    }	

    public static void clientAuth() {
        try {
        	ExecutorService executorService = Executors.newFixedThreadPool(5);
        	String tenant = "tenant.domain.com";
        	String username = "email@example.com";
        	String password = "thePass";
        	String apiserver = "https://accountname.restv2.regionname.media.azure.net/api/";

        	// Setup Azure AD Credentials (in this case using username and password)
        	AzureAdTokenCredentials credentials = new AzureAdTokenCredentials(
        			tenant,
        			new AzureAdClientUsernamePassword(username, password),
        			AzureEnvironments.AzureCloudEnvironment);

        	AzureAdTokenProvider provider = new AzureAdTokenProvider(credentials, executorService);

        	// create a new configuration with the new credentials
        	Configuration configuration = MediaConfiguration.configureWithAzureAdTokenProvider(
        			new URI(apiserver),
        			provider);

        	// create the media service provisioned with the new configuration
        	MediaContract mediaService = MediaService.create(configuration);

            System.out.println("Listing assets");

            ListResult<AssetInfo> assets = mediaService.list(Asset.list());

            for (AssetInfo asset : assets) {
                System.out.println(asset.getId());
            }

            executorService.shutdown();

        } catch (ServiceException se) {
            System.out.println("ServiceException encountered.");
            System.out.println(se.toString());
        } catch (Throwable e) {
            System.out.println("Exception encountered.");
            e.printStackTrace();
            System.out.println(e.toString());
        }
    }

    public static void servicePrincipalAuth() {
        try {
        	ExecutorService executorService = Executors.newFixedThreadPool(5);
        	String tenant = "tenant.domain.com";
        	String clientId = "<client id>";
        	String clientKey = "<client key>";
        	String apiserver = "https://accountname.restv2.regionname.media.azure.net/api/";

        	// Setup Azure AD Credentials (in this case using username and password)
        	AzureAdTokenCredentials credentials = new AzureAdTokenCredentials(
        			tenant,
        			new AzureAdClientSymmetricKey(clientId, clientKey),
        			AzureEnvironments.AzureCloudEnvironment);

        	AzureAdTokenProvider provider = new AzureAdTokenProvider(credentials, executorService);

        	// create a new configuration with the new credentials
        	Configuration configuration = MediaConfiguration.configureWithAzureAdTokenProvider(
        			new URI(apiserver),
        			provider);

        	// create the media service provisioned with the new configuration
        	MediaContract mediaService = MediaService.create(configuration);

            System.out.println("Listing assets");

            ListResult<AssetInfo> assets = mediaService.list(Asset.list());

            for (AssetInfo asset : assets) {
                System.out.println(asset.getId());
            }

            executorService.shutdown();

        } catch (ServiceException se) {
            System.out.println("ServiceException encountered.");
            System.out.println(se.toString());
        } catch (Throwable e) {
            System.out.println("Exception encountered.");
            e.printStackTrace();
            System.out.println(e.toString());
        }
    }

    public static void servicePrincipalCertificate() {
        try {
        	ExecutorService executorService = Executors.newFixedThreadPool(5);
        	String tenant = "tenant.domain.com";
        	String clientId = "<client id>";
        	String apiserver = "https://accountname.restv2.regionname.media.azure.net/api/";
        	InputStream pfx = new FileInputStream("C://repos/000-nogoya/cert/keystore.pfx");
        	String pfxPassword = "1234";


        	// Setup Azure AD Credentials (in this case using username and password)
        	AzureAdTokenCredentials credentials = new AzureAdTokenCredentials(
        			tenant,
        			AsymmetricKeyCredential.create(clientId, pfx, pfxPassword),
        			AzureEnvironments.AzureCloudEnvironment);

        	AzureAdTokenProvider provider = new AzureAdTokenProvider(credentials, executorService);

        	// create a new configuration with the new credentials
        	Configuration configuration = MediaConfiguration.configureWithAzureAdTokenProvider(
        			new URI(apiserver),
        			provider);

        	// create the media service provisioned with the new configuration
        	MediaContract mediaService = MediaService.create(configuration);

            System.out.println("Listing assets");

            ListResult<AssetInfo> assets = mediaService.list(Asset.list());

            for (AssetInfo asset : assets) {
                System.out.println(asset.getId());
            }

            executorService.shutdown();

        } catch (ServiceException se) {
            System.out.println("ServiceException encountered.");
            System.out.println(se.toString());
        } catch (Throwable e) {
            System.out.println("Exception encountered.");
            e.printStackTrace();
            System.out.println(e.toString());
        }
    }
}