# Get Started

This folder contains 3 samples showing different authentication scenarios to connect with Azure Media Services:

* Service principal with client symmetric key.
* Service principal and client certificate.
* User/Pass authentication scenario.

Notice that these scenarios rely on Azure Active Directory authentication.

# Prerequisites

* A Media Services account in a new or existing Azure subscription. See the topic [How to Create a Media Services Account](http://go.microsoft.com/fwlink/?linkid=256662).
* Make sure you review the [Accessing Azure Media Services API with Azure AD authentication overview](https://docs.microsoft.com/en-us/azure/media-services/media-services-use-aad-auth-to-access-ams-api).

# Connect to Media Services API with service principal and client symmetric key

To connect to the Media Services API by using the service principal option, you need to obtain the service principal credentials from the Azure Portal, see here [how to configure Service principal authentication](https://docs.microsoft.com/en-us/azure/media-services/media-services-portal-get-started-with-aad#service-principal-authentication) and obtain the parameters required by this sample.

The following code shows how to connect a service to an Azure Media Services account by using the service principal authentication option. Once you got the credentials, follow steps below:

1. Get the [source code](src/main/java/com/microsoft/windowsazure/services/media/samples/azuread/ServicePrincipalWithSymmetricKey.java)
1. Open it in your preferred Java IDE
1. Substitute the values of the following variables with parameters obtained from the Azure portal:
    * `tenant`: Set the tenant domain name which the user account belongs to, (e.g `microsoft.onmicrosoft.com`)
    * `clientId`: The client id obtained from the Azure portal.
    * `clientKey`: TThe client secret obtained from the Azure portal.
    * `restApiEndpoint` The Azure Media Services account REST API endpoint.

    Hint: Look up this code snippet and replace values:

        String tenant = "tenant.domain.com";
        String clientId = "%client_id%";
        String clientKey = "%client_key%";
        String restApiEndpoint = "https://account.restv2.region.media.azure.net/api/";

4. Run the sample.

# Connect to Media Services API with service principal and client certificate

TBD

* [Complete sample source code](src/main/java/com/microsoft/windowsazure/services/media/samples/azuread/ServicePrincipalWithClientCertificate.java)

# Connect to Media Services API with user/password authentication

The following code shows how to authenticate a person to interact with Azure Media Services resources given its user credentials (i.e. username and password). Note that this mechanism relies on providing the username and the password programmatically.

In order to run this sample you should:

1. Get the [source code](src/main/java/com/microsoft/windowsazure/services/media/samples/azuread/UserPassAuth.java)
1. Open it in your preferred Java IDE
1. Substitute the values of the following variables:
    * `tenant`: Set the tenant domain name which the user account belongs to, (e.g `microsoft.onmicrosoft.com`)
    * `username`: The user email address.
    * `password`: The user password.
    * `restApiEndpoint` The Azure Media Services account REST API endpoint.

    Hint: Look up this code snippet and replace values:

        String tenant = "tenant.domain.com";
        String username = "email@example.com";
        String password = "thePass";
        String restApiEndpoint = "https://account.restv2.region.media.azure.net/api/";

4. Run the sample.