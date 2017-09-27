package com.microsoft.windowsazure.services.media.samples.scaleworkflow;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.lang.StringUtils;

import com.microsoft.windowsazure.Configuration;
import com.microsoft.windowsazure.exception.ServiceException;
import com.microsoft.windowsazure.services.media.MediaConfiguration;
import com.microsoft.windowsazure.services.media.MediaContract;
import com.microsoft.windowsazure.services.media.MediaService;
import com.microsoft.windowsazure.services.media.OperationUtils;
import com.microsoft.windowsazure.services.media.authentication.AzureAdClientSymmetricKey;
import com.microsoft.windowsazure.services.media.authentication.AzureAdTokenCredentials;
import com.microsoft.windowsazure.services.media.authentication.AzureAdTokenProvider;
import com.microsoft.windowsazure.services.media.authentication.AzureEnvironments;
import com.microsoft.windowsazure.services.media.models.EncodingReservedUnit;
import com.microsoft.windowsazure.services.media.models.EncodingReservedUnitInfo;
import com.microsoft.windowsazure.services.media.models.EncodingReservedUnitType;
import com.microsoft.windowsazure.services.media.models.ListResult;
import com.microsoft.windowsazure.services.media.models.StorageAccountInfo;
import com.microsoft.windowsazure.services.media.models.StorageAccounts;
import com.microsoft.windowsazure.services.media.models.StreamingEndpoint;
import com.microsoft.windowsazure.services.media.models.StreamingEndpointInfo;

public final class Program {

    private static MediaContract mediaService;

    // Media Services account credentials configuration
    private static String tenant = "tenant.domain.com";
    private static String clientId = "<client id>";
    private static String clientKey = "<client key>";
    private static String restApiEndpoint = "https://accountname.restv2.regionname.media.azure.net/api/";

    // Utility classes should not have a public or default constructor
    private Program() {
    }

    public static void main(String[] args) {
        try {
            ExecutorService executorService = Executors.newFixedThreadPool(5);

            // Connect to Media Services API with service principal and client symmetric key
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

            System.out.println("Azure SDK for Java - Scale Workflow Sample");

            // 1. Summary of Storage accounts
            println("1 - Storage Accounts ");

            // 1.1 List the Storage accounts attached to the Media Services account
            ListResult<StorageAccountInfo> storageAccounts = mediaService.list(StorageAccounts.list());

            // 1.2 Format output
            println(StringUtils.repeat("-", 61));
            println(String.format("%-32s %-10s %16s", "STORAGE ACCOUNT", "IS DEFAULT", "BYTES USED"));
            println(StringUtils.repeat("=", 61));

            for(StorageAccountInfo storageAccountInfo : storageAccounts) {
                println(String.format("%-32s %-10s %16d", storageAccountInfo.getName(),
                        (storageAccountInfo.isDefault() ? "true" : ""), storageAccountInfo.getBytesUsed()));
            }

            println(StringUtils.repeat("-", 61));
            println();

            // 2. Summary of Streaming Endpoints
            println("2 - Streaming Endpoints ");

            // 2.1 List the existing Streaming Endpoints
            ListResult<StreamingEndpointInfo> streamingEndpoints = mediaService.list(StreamingEndpoint.list());

            // 2.2 Format output
            println(StringUtils.repeat("-", 144));
            println(String.format("%-32s %-14s %-14s %-22s %s", "NAME", "STATUS", "CDN", "STREAMING UNITS", "HOST NAME"));
            println(StringUtils.repeat("=", 144));
            for(StreamingEndpointInfo streamingEndpointInfo : streamingEndpoints) {
                println(String.format("%-32s %-14s %-14s %-22d %s",
                            streamingEndpointInfo.getName(),
                            streamingEndpointInfo.getState().getCode(),
                            streamingEndpointInfo.isCdnEnabled() ? "Enabled" : "Disabled",
                            streamingEndpointInfo.getScaleUnits(),
                            streamingEndpointInfo.getHostName()));
            }
            println(StringUtils.repeat("-", 144));
            println();

            // 3. Summary of Encoding Units
            System.out.println("3 - Encoding Reserved Units ");

            // 3.1 Get the current Encoding Reserved Units information
            EncodingReservedUnitInfo encodingReservedUnit = mediaService.get(EncodingReservedUnit.get());

            // 3.2 Format output
            System.out.println(StringUtils.repeat("-", 42));
            System.out.println(String.format("RESERVED UNIT TYPE:    %s",  encodingReservedUnit.getReservedUnitType().toString().toUpperCase()));
            System.out.println(String.format("ENCODING UNITS:        %d",  encodingReservedUnit.getCurrentReservedUnits()));
            System.out.println(StringUtils.repeat("-", 42));
            System.out.println();

            // 4. Create a new Steaming Endpoint
            System.out.println("4 - Create a Streaming Endpoint");

            // 4.1 Ask the required information
            if (inYesNo("Do you want to create a new Streaming Endpoint?", true)) {
                String name = inString("Enter the Streaming Endpoint name", 24);
                boolean cdn = inYesNo("Do you want enable CDN?", false);
                int units = inDecimal("Enter the number of Streaming Units", cdn ? 1 : 0, 10);

                boolean doit = inYesNo(String.format("Creating Streaming Endpoint %s, Units: %d, CDN: %s - Confirm?",
                        name, units, cdn ? "ENABLED" : "DISABLED"), true);
                if (doit) {
                    print("Creating...");
                    startSpinner();

                    // 4.2 Create the Streaming Endpoint
                    StreamingEndpointInfo streamingEndpointInfo = mediaService.create(
                            StreamingEndpoint.create().setName(name)
                            .setCdnEnabled(cdn)
                            .setScaleUnits(units));

                    // 4.3 Wait for operation completed.
                    OperationUtils.await(mediaService, streamingEndpointInfo);

                    stopSpinner();
                    println(" done!");
                } else {
                    println(" cancelled!");
                }
            }

            // 5. Scale the Encoding Units
            System.out.println("5 - Scale Encoding Units");

            // 5.1 Ask the required information
            if (inYesNo("Do you want to scale Encoding Reserved Units?", true)) {
                int units = inDecimal("Enter the Encoding Reserved Units", 1 , 25);
                int type = inDecimal("Enter the Unit Type: 0-BASIC, 1-STANDAR, 2-PREMIUM", 0, 2);
                boolean doit = inYesNo(String.format("Scaling Encoding Reserved Units: %d, Type: %s - Confirm?",
                        units, EncodingReservedUnitType.fromCode(type).toString().toUpperCase()), true);

                if (doit) {
                    print("Updating...");
                    startSpinner();

                    // 5.2 Update the EncodingReservedUnit
                    encodingReservedUnit = mediaService.get(EncodingReservedUnit.get());
                    String opId = mediaService.update(EncodingReservedUnit.update(encodingReservedUnit)
                            .setCurrentReservedUnits(units)
                            .setReservedUnitType(EncodingReservedUnitType.fromCode(type)));

                    // 5.3 Wait for operation completed.
                    OperationUtils.await(mediaService, opId);

                    stopSpinner();
                    println(" done!");
                } else {
                    println(" cancelled!");
                }
            }

            executorService.shutdown();

        } catch (ServiceException se) {
            System.out.println("ServiceException encountered.");
            System.out.println(se.toString());
        } catch (Exception e) {
            System.out.println("Exception encountered.");
            System.out.println(e.toString());
        }
    }

    private static int inDecimal(String message, int min, int max) throws IOException {
        while(true) {
            System.out.print(String.format("%s:", message));
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            String in = br.readLine().trim();
            System.out.println();
            int result;
            try {
                result = Integer.parseInt(in);
                if (result < min || result > max) {
                    throw new NumberFormatException();
                }
                return result;
            } catch(NumberFormatException e) {
                System.out.println(String.format("The input must be a number between %d and %d.", min, max));
            }
        }
    }

    private static String inString(String message, int limit) throws IOException {
        while(true) {
            System.out.print(String.format("%s%s:", message, limit > 0 ? " (max:" + limit +")": ""));
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            String in = br.readLine().trim();
            System.out.println();
            if (limit > 0 && in.length() < limit) {
                return in;
            } else {
                System.out.println(String.format("The input must not exceed %d charactes.", limit));
            }
        }
    }

    private static boolean inYesNo(String message, boolean defaultYes) throws IOException {
        while(true) {
            System.out.print(String.format("%s [%s/%s]:", message, defaultYes ? "Y" : "y", defaultYes ? "n" : "N"));
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            String in = br.readLine().trim().toUpperCase();
            System.out.println();
            if ((defaultYes && in.isEmpty()) || in.equals("YES") || in.equals("Y")) {
                return true;
            } else
            if ((!defaultYes && in.isEmpty()) || in.equals("NO") || in.equals("N")) {
                return false;
            } else {
                System.out.println("Please answer Y/Yes or N/No!");
            }
        }
    }

    private static void print(String string) {
        System.out.print(string);
    }

    private static void println() {
        System.out.println();
    }

    private static void println(String string) {
        System.out.println(string);
    }

    private static Thread spinner = null;
    public static void startSpinner() {
        if (spinner != null) {
            return;
        }
        spinner = new Thread(new Runnable() {
            public void run() {
                String[] spinner = new String[] {"\u0008/", "\u0008-", "\u0008\\", "\u0008|" };
                int i = 0;
                try {
                    while(true) {
                        Thread.sleep(150);
                        System.out.print(String.format("%s", spinner[i++ % spinner.length]));
                    }
                } catch (InterruptedException e) {
                    System.out.print(String.format("%s", "\u0008"));
                }
            }
        });
        spinner.start();
    }

    public static void stopSpinner() {
        if (spinner != null) {
            spinner.interrupt();
            try {
                spinner.join();
                spinner = null;
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }
}