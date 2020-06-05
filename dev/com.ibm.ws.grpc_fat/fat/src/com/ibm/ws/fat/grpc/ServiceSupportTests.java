/*******************************************************************************
 * Copyright (c) 2020 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.ws.fat.grpc;

import static org.junit.Assert.assertTrue;

import java.util.Set;
import java.util.logging.Logger;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import com.ibm.websphere.simplicity.ShrinkHelper;
import com.ibm.ws.grpc.fat.beer.service.Beer;
import com.ibm.ws.grpc.fat.beer.service.BeerResponse;
import com.ibm.ws.grpc.fat.beer.service.BeerServiceGrpc;
import com.ibm.ws.grpc.fat.beer.service.BeerServiceGrpc.BeerServiceBlockingStub;

import componenttest.annotation.ExpectedFFDC;
import componenttest.annotation.Server;
import componenttest.custom.junit.runner.FATRunner;
import componenttest.topology.impl.LibertyServer;
import componenttest.topology.utils.FATServletClient;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.examples.helloworld.GreeterGrpc;
import io.grpc.examples.helloworld.GreeterGrpc.GreeterBlockingStub;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;

//Beer newBeer1 = new Beer("Citraquenchl","Heist",NEWENGLANDIPA, 4.3);
//Beer newBeer2 = new Beer("Snozzberry","Green Man Brewery",AMERICANWILDALE, 4.2);
//Beer newBeer3 = new Beer("Red Angel","Wicked Weed Brewing",AMERICANWILDALE, 4.4);
//Beer newBeer4 = new Beer("The Event Horizon","Olde Hickory Brewery",AMERICANIMPERIALSTOUT, 4.2);
//Beer newBeer5 = new Beer("Blurred Is The Word","Heist",NEWENGLANDIPA, 4.2);
//Beer newBeer6 = new Beer("Drunken Vigils Breakfast Stout","Southern Pines Brewing Company",AMERICANIMPERIALSTOUT, 4.4);

@RunWith(FATRunner.class)
public class ServiceSupportTests extends FATServletClient {

    protected static final Class<?> c = ServiceSupportTests.class;
    private static final long serialVersionUID = 1L;

    private static final Logger LOG = Logger.getLogger(c.getName());

    public static final int APP_STARTUP_TIMEOUT = 120 * 1000; // 120 seconds

    ManagedChannel beerChannel, worldChannel;
    private BeerServiceBlockingStub beerServiceBlockingStub;
    private GreeterBlockingStub worldServiceBlockingStub;

    @Rule
    public TestName name = new TestName();

    @Server("GrpcServer")
    public static LibertyServer grpcServer;

    @BeforeClass
    public static void setUp() throws Exception {
        grpcServer.startServer(ServiceSupportTests.class.getSimpleName() + ".log");
    }

    @AfterClass
    public static void tearDown() throws Exception {
        grpcServer.stopServer();
    }

    private void startBeerService(String address, int port) {
        System.out.println("Connecting to beerService gRPC service at " + address + ":" + port);
        beerChannel = ManagedChannelBuilder.forAddress(address, port).usePlaintext().build();
        beerServiceBlockingStub = BeerServiceGrpc.newBlockingStub(beerChannel);
    }

    private void stopGrpcService(ManagedChannel channel) {
        channel.shutdownNow();
    }

    private void startHelloWorldService(String address, int port) {
        System.out.println("Connecting to helloWorld gRPC service at " + address + ":" + port);
        worldChannel = ManagedChannelBuilder.forAddress(address, port).usePlaintext().build();
        worldServiceBlockingStub = GreeterGrpc.newBlockingStub(worldChannel);
    }

    /**
     * Tests validate the two grpc features, server and client
     *
     * @throws Exception
     *
     **/
    @Test
    public void featuresEnabled() throws Exception {
        Set<String> features = grpcServer.getServerConfiguration().getFeatureManager().getFeatures();

        assertTrue("Expected the grpc feature 'grpc-1.0' to be enabled but was not: " + features,
                   features.contains("grpc-1.0"));

        assertTrue("Expected the grpc feature 'grpcClient-1.0' to be enabled but was not: " + features,
                   features.contains("grpcClient-1.0"));

    }

    /**
     * Tests a single grpc service by
     * - installing the grpc servlet app
     * - starting the grpc service
     * - making sure we can reach the servlet
     * - stopping the grpc service
     * - uninstalling the app
     *
     * @throws Exception
     *
     **/
    @Test
    public void testSingleWarWithGrpcService() throws Exception {
        LOG.info("testSingleWarWithGrpcService() : add FavoriteBeerService to the server if not already present.");
        // add all classes from com.ibm.ws.grpc.fat.beer.service and com.ibm.ws.grpc.fat.beer
        // to a new app FavoriteBeerService.war
        ShrinkHelper.defaultDropinApp(grpcServer, "FavoriteBeerService.war",
                                      "com.ibm.ws.grpc.fat.beer.service",
                                      "com.ibm.ws.grpc.fat.beer");

        // Make sure the beer service has started
        String appStarted = grpcServer.waitForStringInLog("Registered gRPC service at URL: /beer", APP_STARTUP_TIMEOUT);
        if (appStarted == null) {
            Assert.fail(c + ": application " + "FavoriteBeerService" + " failed to start within " + APP_STARTUP_TIMEOUT + "ms");
        }

        LOG.info("testSingleWarWithGrpcService() : FavoriteBeerService grpc application has started.");

        // Start up the client connection and send a request
        startBeerService(grpcServer.getHostname(), grpcServer.getHttpDefaultPort());

        // Send a request to add a new beer
        // "Citraquenchl", "Heist", NEWENGLANDIPA, 4.3);
        LOG.info("testSingleWarWithGrpcService() : Add a new beer.");
        Beer newBeer1 = Beer.newBuilder().setBeerName("Citraquenchl").setBeerMaker("Heist").setBeerTypeValue(0).setBeerRating((float) 4.3).build();
        BeerResponse rsp = beerServiceBlockingStub.addBeer(newBeer1);
        assertTrue(rsp.getDone());

        // Stop the grpc service
        stopGrpcService(beerChannel);

        // Stop the grpc application
        LOG.info("testSingleWarWithGrpcService() : Stop the FavoriteBeerService application by removing it from dropins.");
        grpcServer.removeDropinsApplications("FavoriteBeerService");

        //TODO check to make sure it has stopped

    }

    /**
     * /**
     * Tests a multiple grpc services by
     * - installing two grpc servlet apps
     * - starting two grpc services
     * - making sure we can reach both
     * - stopping the grpc services
     * - uninstalling the apps
     *
     * @throws Exception
     *
     **/
    @Test
    public void testMultipleGrpcServiceWars() throws Exception {
        LOG.info("testMultipleWarWithGrpcService() : add FavoriteBeerService to the server if not already present.");
        // add all classes from com.ibm.ws.grpc.fat.beer.service and com.ibm.ws.grpc.fat.beer
        // to a new app FavoriteBeerService.war
        ShrinkHelper.defaultDropinApp(grpcServer, "FavoriteBeerService.war",
                                      "com.ibm.ws.grpc.fat.beer.service",
                                      "com.ibm.ws.grpc.fat.beer");

        // Make sure the beer service has started
        String appStarted = grpcServer.waitForStringInLog("Registered gRPC service at URL: /beer", APP_STARTUP_TIMEOUT);
        if (appStarted == null) {
            Assert.fail(c + ": application " + "FavoriteBeerService" + " failed to start within " + APP_STARTUP_TIMEOUT + "ms");
        }
        LOG.info("testMultipleWarWithGrpcService() : FavoriteBeerService grpc application has started.");

        // add all classes from com.ibm.ws.grpc.fat.helloworld.service and io.grpc.examples.helloworld
        // to a new app HelloWorldService.war
        ShrinkHelper.defaultDropinApp(grpcServer, "HelloWorldService.war",
                                      "com.ibm.ws.grpc.fat.helloworld.service",
                                      "io.grpc.examples.helloworld");

        // Make sure the helloworld service has started
        appStarted = grpcServer.waitForStringInLog("Registered gRPC service at URL: /hello", APP_STARTUP_TIMEOUT);
        if (appStarted == null) {
            Assert.fail(c + ": application " + "HelloWorldService" + " failed to start within " + APP_STARTUP_TIMEOUT + "ms");
        }
        LOG.info("testMultipleWarWithGrpcService() : HelloWorldService grpc application has started.");

        // Start two grpc services
        startBeerService(grpcServer.getHostname(), grpcServer.getHttpDefaultPort());
        startHelloWorldService(grpcServer.getHostname(), grpcServer.getHttpDefaultPort());

        // Send a request to the Beer service and check to see if it was added
        Beer newBeer1 = Beer.newBuilder().setBeerName("Citraquenchl").setBeerMaker("Heist").setBeerTypeValue(0).setBeerRating((float) 4.3).build();
        BeerResponse rsp = beerServiceBlockingStub.addBeer(newBeer1);
        assertTrue(rsp.getDone());

        // Send a request to the HelloWorld service and check for a response
        HelloRequest person = HelloRequest.newBuilder().setName("Scarlett").build();
        HelloReply greeting = worldServiceBlockingStub.sayHello(person);
        //Make sure the reply has Scarlett in it
        assertTrue(greeting.getMessage().contains("Scarlett"));

        // Stop the grpc services
        stopGrpcService(beerChannel);
        stopGrpcService(worldChannel);

        // Stop the grpc applications
        LOG.info("testMultipleWarWithGrpcService() : Stop the FavoriteBeerService and HelloworldService applications by removing them from dropins.");
        grpcServer.removeDropinsApplications("FavoriteBeerService.war");
        grpcServer.removeDropinsApplications("HelloWorldService.war");

        //TODO check to make sure it has stopped

    }

    /**
     * Tests single war update
     * Install the same app twice, which should cause an update to the app
     *
     * @throws Exception
     *
     **/
    @Test
    public void testSingleWarUpdate() throws Exception {

        LOG.info("testSingleWarUpdate() : add FavoriteBeerService to the server if not already present.");
        // add all classes from com.ibm.ws.grpc.fat.beer.service and com.ibm.ws.grpc.fat.beer
        // to a new app FavoriteBeerService.war
        ShrinkHelper.defaultDropinApp(grpcServer, "FavoriteBeerService.war",
                                      "com.ibm.ws.grpc.fat.beer.service",
                                      "com.ibm.ws.grpc.fat.beer");

        // Make sure the beer service has started
        String appStarted = grpcServer.waitForStringInLog("Registered gRPC service at URL: /beer", APP_STARTUP_TIMEOUT);
        if (appStarted == null) {
            Assert.fail(c + ": application " + "FavoriteBeerService" + " failed to start within " + APP_STARTUP_TIMEOUT + "ms");
        }
        LOG.info("testSingleWarUpdate() : FavoriteBeerService grpc application has started.");

        startBeerService(grpcServer.getHostname(), grpcServer.getHttpDefaultPort());

        // Send a request to add a new beer and make sure it was added
        // "Citraquenchl", "Heist", NEWENGLANDIPA, 4.3);
        LOG.info("testSingleWarUpdate() : Add a new beer.");
        Beer newBeer1 = Beer.newBuilder().setBeerName("Citraquenchl").setBeerMaker("Heist").setBeerTypeValue(0).setBeerRating((float) 4.3).build();
        BeerResponse rsp = beerServiceBlockingStub.addBeer(newBeer1);
        assertTrue(rsp.getDone());

        // Add the same application to the dropins folder again so that it is updated
        ShrinkHelper.defaultDropinApp(grpcServer, "FavoriteBeerService.war",
                                      "com.ibm.ws.grpc.fat.beer.service",
                                      "com.ibm.ws.grpc.fat.beer");

        // Make sure the beer service has started
        String appStarted2 = grpcServer.waitForStringInLog("Registered gRPC service at URL: /beer", APP_STARTUP_TIMEOUT);
        if (appStarted == null) {
            Assert.fail(c + ": application " + "FavoriteBeerService" + " failed to start within " + APP_STARTUP_TIMEOUT + "ms");
        }
        LOG.info("testSingleWarUpdate() : FavoriteBeerService grpc application has started for the second time.");

        startBeerService(grpcServer.getHostname(), grpcServer.getHttpDefaultPort());

        // Send a request to add a new beer
        // "Snozzberry","Green Man Brewery",AMERICANWILDALE, 4.2);
        LOG.info("testSingleWarUpdate() : Add a second new beer.");
        Beer newBeer2 = Beer.newBuilder().setBeerName("Snozzberry").setBeerMaker("Green Man Brewery").setBeerTypeValue(1).setBeerRating((float) 4.2).build();
        BeerResponse rsp2 = beerServiceBlockingStub.addBeer(newBeer2);

        assertTrue(rsp2.getDone());

        stopGrpcService(beerChannel);

        // Stop the grpc application
        LOG.info("testMultipleWarWithGrpcService() : Stop the FavoriteBeerService and HelloworldService applications by removing them from dropins.");
        grpcServer.removeDropinsApplications("FavoriteBeerService.war");

        //TODO check to make sure it has stopped

    }

    /**
     * Tests invalid grpc service
     *
     * @throws Exception
     *
     **/
//    @Test
    public void testInvalidService() throws Exception {

        startBeerService(grpcServer.getHostname(), grpcServer.getHttpDefaultPort());
        startHelloWorldService(grpcServer.getHostname(), grpcServer.getHttpDefaultPort());

        stopGrpcService(beerChannel);
        stopGrpcService(worldChannel);

    }

    /**
     * Tests duplicate grpc service
     *
     * @throws Exception
     *
     **/
    @Test
    @ExpectedFFDC({ "java.lang.RuntimeException", "java.lang.Exception" })
    public void testDuplicateService() throws Exception {
        LOG.info("testSingleWarUpdate() : add FavoriteBeerService to the server if not already present.");
        // add all classes from com.ibm.ws.grpc.fat.beer.service and com.ibm.ws.grpc.fat.beer
        // to a new app FavoriteBeerService.war
        ShrinkHelper.defaultDropinApp(grpcServer, "FavoriteBeerService.war",
                                      "com.ibm.ws.grpc.fat.beer.service",
                                      "com.ibm.ws.grpc.fat.beer");

        // Make sure the beer service has started
        String appStarted = grpcServer.waitForStringInLog("Registered gRPC service at URL: /beer", APP_STARTUP_TIMEOUT);
        if (appStarted == null) {
            Assert.fail(c + ": application " + "FavoriteBeerService" + " failed to start within " + APP_STARTUP_TIMEOUT + "ms");
        }
        LOG.info("testSingleWarUpdate() : FavoriteBeerService grpc application has started.");

        LOG.info("testSingleWarUpdate() : add FavoriteBeerService to the server if not already present.");
        // add all classes from com.ibm.ws.grpc.fat.beer.service and com.ibm.ws.grpc.fat.beer
        // to a new app FavoriteBeerService.war
        ShrinkHelper.defaultDropinApp(grpcServer, "FavoriteBeerService2.war",
                                      "com.ibm.ws.grpc.fat.beer.service",
                                      "com.ibm.ws.grpc.fat.beer");

        // It's invalid to have the same grpc services served by two different apps, this should generate an error
        //CWWKZ0012I: The application FavoriteBeerService2 was not started.
        String app2NotStarted = grpcServer.waitForStringInLog("CWWKZ0012I: The application FavoriteBeerService2 was not started.", APP_STARTUP_TIMEOUT);
        if (app2NotStarted == null) {
            Assert.fail(c + ": application " + "FavoriteBeerService2" + " started in error within  " + APP_STARTUP_TIMEOUT + "ms");
        }
        LOG.info("testSingleWarUpdate() : FavoriteBeerService2 grpc application has started.");

        // This generates an NPE on line 88
        //stopBeerService();

        // Stop the grpc application
        LOG.info("testMultipleWarWithGrpcService() : Stop the FavoriteBeerService application by removing it from dropins.");
        grpcServer.removeDropinsApplications("FavoriteBeerService.war");

    }
}
