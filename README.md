# Microsoft Azure SDK for Media Services Java Samples
This repository contains the following samples showing how to use Azure Media Services Java SDK.

* **azure-media-dynamic-encryption-aes**: This sample is a console Java application that shows how to use AES-128 Dynamic Encryption and Key Delivery Service. It is based on this MSDN article: [https://msdn.microsoft.com/en-us/library/azure/dn783457.aspx](https://msdn.microsoft.com/en-us/library/azure/dn783457.aspx).
* **azure-media-dynamic-encryption-playready**: This sample is a console Java application that shows how to use PlayReady Dynamic Encryption and License Delivery Service. It is based on this MSDN article: [https://msdn.microsoft.com/en-us/library/azure/dn783467.aspx](https://msdn.microsoft.com/en-us/library/azure/dn783467.aspx).
* **azure-media-scale-workflow**: This sample is a console Java application that shows how to scale Encoding Reserved Units and Streaming Endpoints.

These samples rely on the [azure-media maven package v0.8.0](http://mvnrepository.com/artifact/com.microsoft.azure/azure-media/0.8.0).

```xml
<dependency>
  <groupId>com.microsoft.azure</groupId>
  <artifactId>azure-media</artifactId>
  <version>0.8.0</version>
</dependency>
```
