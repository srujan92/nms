package org.motechproject.nms.api.osgi;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.joda.time.DateTime;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.motechproject.nms.flw.domain.FrontLineWorker;
import org.motechproject.nms.flw.domain.Service;
import org.motechproject.nms.flw.domain.ServiceUsage;
import org.motechproject.nms.flw.domain.ServiceUsageCap;
import org.motechproject.nms.flw.repository.FrontLineWorkerDataService;
import org.motechproject.nms.flw.repository.ServiceUsageCapDataService;
import org.motechproject.nms.flw.repository.ServiceUsageDataService;
import org.motechproject.nms.flw.service.FrontLineWorkerService;
import org.motechproject.nms.kilkari.domain.Subscriber;
import org.motechproject.nms.kilkari.domain.Subscription;
import org.motechproject.nms.kilkari.domain.SubscriptionPack;
import org.motechproject.nms.kilkari.repository.SubscriberDataService;
import org.motechproject.nms.kilkari.repository.SubscriptionDataService;
import org.motechproject.nms.kilkari.repository.SubscriptionPackDataService;
import org.motechproject.nms.kilkari.service.KilkariService;
import org.motechproject.nms.language.domain.CircleLanguage;
import org.motechproject.nms.language.domain.Language;
import org.motechproject.nms.language.repository.CircleLanguageDataService;
import org.motechproject.nms.language.repository.LanguageDataService;
import org.motechproject.testing.osgi.BasePaxIT;
import org.motechproject.testing.osgi.container.MotechNativeTestContainerFactory;
import org.motechproject.testing.osgi.http.SimpleHttpClient;
import org.motechproject.testing.utils.TestContext;
import org.ops4j.pax.exam.ExamFactory;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertTrue;


/**
 * Verify that LanguageService HTTP service is present and functional.
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
@ExamFactory(MotechNativeTestContainerFactory.class)
public class UserControllerBundleIT extends BasePaxIT {
    private static final String ADMIN_USERNAME = "motech";
    private static final String ADMIN_PASSWORD = "motech";

    @Inject
    private KilkariService kilkariService;

    @Inject
    private SubscriberDataService subscriberDataService;

    @Inject
    private SubscriptionPackDataService subscriptionPackDataService;

    @Inject
    private SubscriptionDataService subscriptionDataService;

    @Inject
    private FrontLineWorkerService frontLineWorkerService;

    @Inject
    private FrontLineWorkerDataService frontLineWorkerDataService;

    @Inject
    private ServiceUsageDataService serviceUsageDataService;

    @Inject
    private ServiceUsageCapDataService serviceUsageCapDataService;

    @Inject
    private LanguageDataService languageDataService;

    @Inject
    private CircleLanguageDataService circleLanguageDataService;

    // TODO: Clean up data creation and cleanup
    private void cleanAllData() {
        subscriptionDataService.deleteAll();
        subscriptionPackDataService.deleteAll();
        subscriberDataService.deleteAll();
        serviceUsageCapDataService.deleteAll();
        serviceUsageDataService.deleteAll();
        frontLineWorkerDataService.deleteAll();
        circleLanguageDataService.deleteAll();
        languageDataService.deleteAll();
    }

    /*
    Creates two subscription packs ('pack1' and 'pack2')
    Create two subscribers:
        Subscriber "0000000000" is subscribed to pack 'pack1'
        Subscriber "0000000001" is subscribed to packs 'pack1' and 'pack2'
     */
    private void createKilkariTestData() {
        cleanAllData();

        SubscriptionPack pack1 = subscriptionPackDataService.create(new SubscriptionPack("pack1"));
        SubscriptionPack pack2 = subscriptionPackDataService.create(new SubscriptionPack("pack2"));
        List<SubscriptionPack> onePack = Arrays.asList(pack1);
        List<SubscriptionPack> twoPacks = Arrays.asList(pack1, pack2);

        Subscriber subscriber1 = subscriberDataService.create(new Subscriber("0000000000"));
        Subscriber subscriber2 = subscriberDataService.create(new Subscriber("0000000001"));

        Subscription subscription1 = subscriptionDataService.create(new Subscription("001", subscriber1, pack1));
        Subscription subscription2 = subscriptionDataService.create(new Subscription("002", subscriber2, pack1));
        Subscription subscription3 = subscriptionDataService.create(new Subscription("003", subscriber2, pack2));
    }

    private void createFLWCappedServiceNoUsageNoLocationNoLanguage() {
        cleanAllData();

        FrontLineWorker flw = new FrontLineWorker("Frank Llyod Wright", "0000000000");
        frontLineWorkerService.add(flw);

        Language language = new Language("Papiamento", 99);
        languageDataService.create(language);

        CircleLanguage circleLanguage = new CircleLanguage("AA", language);
        circleLanguageDataService.create(circleLanguage);

        ServiceUsageCap serviceUsageCap = new ServiceUsageCap(null, Service.MOBILE_KUNJI, 3600);
        serviceUsageCapDataService.create(serviceUsageCap);
    }

    private void createFLWWithLanguageServiceUsageAndCappedService() {
        cleanAllData();

        Language language = new Language("English", 10);
        languageDataService.create(language);

        FrontLineWorker flw = new FrontLineWorker("Frank Llyod Wright", "0000000000");
        flw.setLanguage(language);
        frontLineWorkerService.add(flw);

        language = new Language("Papiamento", 99);
        languageDataService.create(language);

        CircleLanguage circleLanguage = new CircleLanguage("AA", language);
        circleLanguageDataService.create(circleLanguage);

        ServiceUsageCap serviceUsageCap = new ServiceUsageCap(null, Service.MOBILE_KUNJI, 3600);
        serviceUsageCapDataService.create(serviceUsageCap);

        // A service record without endOfService and WelcomePrompt played
        ServiceUsage serviceUsage = new ServiceUsage(flw, Service.MOBILE_KUNJI, 1, 0, 0, DateTime.now());
        serviceUsageDataService.create(serviceUsage);
    }

    private void createFLWWithLanguageFullServiceUsageAndCappedService() {
        cleanAllData();

        Language language = new Language("English", 10);
        languageDataService.create(language);

        FrontLineWorker flw = new FrontLineWorker("Frank Llyod Wright", "0000000000");
        flw.setLanguage(language);
        frontLineWorkerService.add(flw);

        language = new Language("Papiamento", 99);
        languageDataService.create(language);

        CircleLanguage circleLanguage = new CircleLanguage("AA", language);
        circleLanguageDataService.create(circleLanguage);

        ServiceUsageCap serviceUsageCap = new ServiceUsageCap(null, Service.MOBILE_KUNJI, 3600);
        serviceUsageCapDataService.create(serviceUsageCap);

        ServiceUsage serviceUsage = new ServiceUsage(flw, Service.MOBILE_KUNJI, 1, 1, 1, DateTime.now());
        serviceUsageDataService.create(serviceUsage);
    }

    private void createFLWWithLanguageFullUsageOfBothServiceUncapped() {
        cleanAllData();

        Language language = new Language("English", 10);
        languageDataService.create(language);

        FrontLineWorker flw = new FrontLineWorker("Frank Llyod Wright", "0000000000");
        flw.setLanguage(language);
        frontLineWorkerService.add(flw);

        language = new Language("Papiamento", 99);
        languageDataService.create(language);

        CircleLanguage circleLanguage = new CircleLanguage("AA", language);
        circleLanguageDataService.create(circleLanguage);

        ServiceUsage serviceUsage = new ServiceUsage(flw, Service.MOBILE_KUNJI, 1, 1, 1, DateTime.now());
        serviceUsageDataService.create(serviceUsage);

        // Academy doesn't have a welcome prompt
        serviceUsage = new ServiceUsage(flw, Service.MOBILE_ACADEMY, 1, 1, 0, DateTime.now());
        serviceUsageDataService.create(serviceUsage);

        ServiceUsageCap serviceUsageCap = new ServiceUsageCap(null, Service.MOBILE_KUNJI, 10);
        serviceUsageCapDataService.create(serviceUsageCap);
    }

    private void createCircleWithLanguage() {
        cleanAllData();
        Language language = new Language("Papiamento", 99);
        languageDataService.create(language);

        CircleLanguage circleLanguage = new CircleLanguage("AA", language);
        circleLanguageDataService.create(circleLanguage);
    }

    @Test
    public void testKilkariUserRequest() throws IOException, InterruptedException {
        createKilkariTestData();

        HttpGet httpGet = new HttpGet(String.format("http://localhost:%d/api/kilkari/user?callingNumber=0000000001&operator=OP&circle=AA&callId=0123456789abcde", TestContext.getJettyPort()));

        httpGet.addHeader("Authorization",
                "Basic " + new String(Base64.encodeBase64((ADMIN_USERNAME + ":" + ADMIN_PASSWORD).getBytes())));

        assertTrue(SimpleHttpClient.execHttpRequest(httpGet, "{\"languageLocationCode\":null,\"defaultLanguageLocationCode\":null,\"subscriptionPackList\":[\"pack2\",\"pack1\"]}"));
    }

    @Test
    public void testFLWUserRequestWithoutServiceUsage() throws IOException, InterruptedException {
        createFLWCappedServiceNoUsageNoLocationNoLanguage();

        HttpGet httpGet = new HttpGet(String.format("http://localhost:%d/api/mobilekunji/user?callingNumber=0000000000&operator=OP&circle=AA&callId=0123456789abcde", TestContext.getJettyPort()));

        httpGet.addHeader("Authorization",
                "Basic " + new String(Base64.encodeBase64((ADMIN_USERNAME + ":" + ADMIN_PASSWORD).getBytes())));

        assertTrue(SimpleHttpClient.execHttpRequest(httpGet, "{\"languageLocationCode\":null,\"defaultLanguageLocationCode\":99,\"currentUsageInPulses\":0,\"endOfUsagePromptCounter\":0,\"welcomePromptFlag\":false,\"maxAllowedUsageInPulses\":3600,\"maxAllowedEndOfUsagePrompt\":2}"));
    }

    @Test
    public void testFLWUserRequestWithServiceUsageOnly() throws IOException, InterruptedException {
        createFLWWithLanguageServiceUsageAndCappedService();

        HttpGet httpGet = new HttpGet(String.format("http://localhost:%d/api/mobilekunji/user?callingNumber=0000000000&operator=OP&circle=AA&callId=0123456789abcde", TestContext.getJettyPort()));

        httpGet.addHeader("Authorization",
                "Basic " + new String(Base64.encodeBase64((ADMIN_USERNAME + ":" + ADMIN_PASSWORD).getBytes())));

        assertTrue(SimpleHttpClient.execHttpRequest(httpGet, "{\"languageLocationCode\":10,\"defaultLanguageLocationCode\":99,\"currentUsageInPulses\":1,\"endOfUsagePromptCounter\":0,\"welcomePromptFlag\":false,\"maxAllowedUsageInPulses\":3600,\"maxAllowedEndOfUsagePrompt\":2}"));
    }

    @Test
    public void testFLWUserRequestWithServiceUsageAndEndOfUsageAndWelcomeMsg() throws IOException, InterruptedException {
        createFLWWithLanguageFullServiceUsageAndCappedService();

        HttpGet httpGet = new HttpGet(String.format("http://localhost:%d/api/mobilekunji/user?callingNumber=0000000000&operator=OP&circle=AA&callId=0123456789abcde", TestContext.getJettyPort()));

        httpGet.addHeader("Authorization",
                "Basic " + new String(Base64.encodeBase64((ADMIN_USERNAME + ":" + ADMIN_PASSWORD).getBytes())));

        assertTrue(SimpleHttpClient.execHttpRequest(httpGet, "{\"languageLocationCode\":10,\"defaultLanguageLocationCode\":99,\"currentUsageInPulses\":1,\"endOfUsagePromptCounter\":1,\"welcomePromptFlag\":true,\"maxAllowedUsageInPulses\":3600,\"maxAllowedEndOfUsagePrompt\":2}"));
    }

    @Test
    public void testInvalidServiceName() throws IOException, InterruptedException {
        HttpGet httpGet = new HttpGet(String.format("http://localhost:%d/api/NO_SERVICE/user?callingNumber=0123456789&operator=OP&circle=AA&callId=0123456789abcde", TestContext.getJettyPort()));

        httpGet.addHeader("Authorization",
                "Basic " + new String(Base64.encodeBase64((ADMIN_USERNAME + ":" + ADMIN_PASSWORD).getBytes())));

        //todo: replace with execHttpRequest method that also tests response body (in addition to status code)
        //todo: when it's available in platform: org.motechproject.testing.osgi.http.SimpleHttpClient
        assertTrue(SimpleHttpClient.execHttpRequest(httpGet, HttpStatus.SC_BAD_REQUEST));
    }

    @Test
    @Ignore
    public void testNoCallingNumber() throws IOException, InterruptedException {
        HttpGet httpGet = new HttpGet(String.format("http://localhost:%d/api/kilkari/user?operator=OP&circle=AA&callId=0123456789abcde", TestContext.getJettyPort()));

        httpGet.addHeader("Authorization",
                "Basic " + new String(Base64.encodeBase64((ADMIN_USERNAME + ":" + ADMIN_PASSWORD).getBytes())));

        assertTrue(SimpleHttpClient.execHttpRequest(httpGet, HttpStatus.SC_BAD_REQUEST,
                "{\"failureReason\":\"<callingNumber: Not Present>\"}",
                (String) null, (String) null));
    }

    @Test
    @Ignore
    public void testInvalidCallingNumber() throws IOException, InterruptedException {
        HttpGet httpGet = new HttpGet(String.format("http://localhost:%d/api/kilkari/user?callingNumber=XXXXXXX&operator=OP&circle=AA&callId=0123456789abcde", TestContext.getJettyPort()));

        httpGet.addHeader("Authorization",
                "Basic " + new String(Base64.encodeBase64((ADMIN_USERNAME + ":" + ADMIN_PASSWORD).getBytes())));

        assertTrue(SimpleHttpClient.execHttpRequest(httpGet, HttpStatus.SC_BAD_REQUEST,
                "{\"failureReason\":\"<callingNumber: Invalid>\"}",
                (String) null, (String) null));
    }

    @Test
    @Ignore
    public void testNoOperator() throws IOException, InterruptedException {
        HttpGet httpGet = new HttpGet(String.format("http://localhost:%d/api/kilkari/user?callingNumber=XXXXXXX&circle=AA&callId=0123456789abcde", TestContext.getJettyPort()));

        httpGet.addHeader("Authorization",
                "Basic " + new String(Base64.encodeBase64((ADMIN_USERNAME + ":" + ADMIN_PASSWORD).getBytes())));

        assertTrue(SimpleHttpClient.execHttpRequest(httpGet, HttpStatus.SC_BAD_REQUEST,
                "{\"failureReason\":\"<operator: Not Present>\"}",
                (String) null, (String) null));
    }

    @Test
    @Ignore
    public void testNoCircle() throws IOException, InterruptedException {
        HttpGet httpGet = new HttpGet(String.format("http://localhost:%d/api/kilkari/user?callingNumber=XXXXXXX&operator=OP&callId=0123456789abcde", TestContext.getJettyPort()));

        httpGet.addHeader("Authorization",
                "Basic " + new String(Base64.encodeBase64((ADMIN_USERNAME + ":" + ADMIN_PASSWORD).getBytes())));

        assertTrue(SimpleHttpClient.execHttpRequest(httpGet, HttpStatus.SC_BAD_REQUEST,
                "{\"failureReason\":\"<circle: Not Present>\"}",
                (String) null, (String) null));
    }

    @Test
    public void testNoCallId() throws IOException, InterruptedException {
        HttpGet httpGet = new HttpGet(String.format("http://localhost:%d/api/kilkari/user?callingNumber=XXXXXXX&operator=OP&circle=AA", TestContext.getJettyPort()));

        httpGet.addHeader("Authorization",
                "Basic " + new String(Base64.encodeBase64((ADMIN_USERNAME + ":" + ADMIN_PASSWORD).getBytes())));

        assertTrue(SimpleHttpClient.execHttpRequest(httpGet, HttpStatus.SC_BAD_REQUEST,
                "{\"failureReason\":\"<callId: Not Present>\"}",
                (String) null, (String) null));
    }

    @Test
    @Ignore
    public void testInternalError() throws IOException, InterruptedException {
        HttpGet httpGet = new HttpGet(String.format("http://localhost:%d/api/kilkari/user?callingNumber=XXXXXXX&operator=OP&circle=AA", TestContext.getJettyPort()));

        httpGet.addHeader("Authorization",
                "Basic " + new String(Base64.encodeBase64((ADMIN_USERNAME + ":" + ADMIN_PASSWORD).getBytes())));

        assertTrue(SimpleHttpClient.execHttpRequest(httpGet, HttpStatus.SC_INTERNAL_SERVER_ERROR,
                "{\"failureReason\":\"Internal Error\"}",
                (String) null, (String) null));
    }

    // An FLW that does not exist
    @Test
    public void testGetUserDetailsUnknownUser() throws IOException, InterruptedException {
        createCircleWithLanguage();

        HttpGet httpGet = new HttpGet(String.format("http://localhost:%d/api/mobilekunji/user?callingNumber=9999999999&operator=OP&circle=AA&callId=0123456789abcde", TestContext.getJettyPort()));

        httpGet.addHeader("Authorization",
                "Basic " + new String(Base64.encodeBase64((ADMIN_USERNAME + ":" + ADMIN_PASSWORD).getBytes())));

        assertTrue(SimpleHttpClient.execHttpRequest(httpGet, "{\"languageLocationCode\":null,\"defaultLanguageLocationCode\":99,\"currentUsageInPulses\":0,\"endOfUsagePromptCounter\":0,\"welcomePromptFlag\":false,\"maxAllowedUsageInPulses\":-1,\"maxAllowedEndOfUsagePrompt\":2}"));
    }

    // An FLW with usage for both MA and MK
    @Test
    public void testGetUserDetailsUserOfBothServices() throws IOException, InterruptedException {
        createFLWWithLanguageFullUsageOfBothServiceUncapped();

        HttpGet httpGet = new HttpGet(String.format("http://localhost:%d/api/mobileacademy/user?callingNumber=0000000000&operator=OP&circle=AA&callId=0123456789abcde", TestContext.getJettyPort()));

        httpGet.addHeader("Authorization",
                "Basic " + new String(Base64.encodeBase64((ADMIN_USERNAME + ":" + ADMIN_PASSWORD).getBytes())));

        assertTrue(SimpleHttpClient.execHttpRequest(httpGet, "{\"languageLocationCode\":10,\"defaultLanguageLocationCode\":99,\"currentUsageInPulses\":1,\"endOfUsagePromptCounter\":1,\"welcomePromptFlag\":false,\"maxAllowedUsageInPulses\":-1,\"maxAllowedEndOfUsagePrompt\":2}"));
    }

    // An FLW with usage and a service with a cap
    @Test
    public void testGetUserDetailsServiceCapped() throws IOException, InterruptedException {
        createFLWWithLanguageFullUsageOfBothServiceUncapped();

        HttpGet httpGet = new HttpGet(String.format("http://localhost:%d/api/mobilekunji/user?callingNumber=0000000000&operator=OP&circle=AA&callId=0123456789abcde", TestContext.getJettyPort()));

        httpGet.addHeader("Authorization",
                "Basic " + new String(Base64.encodeBase64((ADMIN_USERNAME + ":" + ADMIN_PASSWORD).getBytes())));

        assertTrue(SimpleHttpClient.execHttpRequest(httpGet, "{\"languageLocationCode\":10,\"defaultLanguageLocationCode\":99,\"currentUsageInPulses\":1,\"endOfUsagePromptCounter\":1,\"welcomePromptFlag\":true,\"maxAllowedUsageInPulses\":10,\"maxAllowedEndOfUsagePrompt\":2}"));
    }

    @Test
    public void testSetLanguageInvalidService() throws IOException, InterruptedException {
        HttpPost httpPost = new HttpPost(String.format("http://localhost:%d/api/NO_SERVICE/languageLocationCode", TestContext.getJettyPort()));
        StringEntity params = new StringEntity("{\"callingNumber\":\"0000000000\",\"callId\":\"123456789012345\",\"languageLocationCode\":10}");
        httpPost.setEntity(params);

        httpPost.addHeader("content-type", "application/json");
        httpPost.addHeader("Authorization",
                "Basic " + new String(Base64.encodeBase64((ADMIN_USERNAME + ":" + ADMIN_PASSWORD).getBytes())));

        assertTrue(SimpleHttpClient.execHttpRequest(httpPost, HttpStatus.SC_BAD_REQUEST,
                "{\"failureReason\":\"<serviceName: Invalid>\"}",
                (String) null, (String) null));
    }

    @Test
    public void testSetLanguageMissingCallingNumber() throws IOException, InterruptedException {
        HttpPost httpPost = new HttpPost(String.format("http://localhost:%d/api/NO_SERVICE/languageLocationCode", TestContext.getJettyPort()));
        StringEntity params = new StringEntity("{\"callId\":\"123456789012345\",\"languageLocationCode\":10}");
        httpPost.setEntity(params);

        httpPost.addHeader("content-type", "application/json");
        httpPost.addHeader("Authorization",
                "Basic " + new String(Base64.encodeBase64((ADMIN_USERNAME + ":" + ADMIN_PASSWORD).getBytes())));

        assertTrue(SimpleHttpClient.execHttpRequest(httpPost, HttpStatus.SC_BAD_REQUEST,
                "{\"failureReason\":\"<callingNumber: Not Present>\"}",
                (String) null, (String) null));
    }

    @Test
    public void testSetLanguageInvalidCallingNumber() throws IOException, InterruptedException {
        HttpPost httpPost = new HttpPost(String.format("http://localhost:%d/api/NO_SERVICE/languageLocationCode", TestContext.getJettyPort()));
        StringEntity params = new StringEntity("{\"callingNumber\":\"0000000000\",\"callId\":\"123456789012345\",\"languageLocationCode\":10}");
        httpPost.setEntity(params);

        httpPost.addHeader("content-type", "application/json");
        httpPost.addHeader("Authorization",
                "Basic " + new String(Base64.encodeBase64((ADMIN_USERNAME + ":" + ADMIN_PASSWORD).getBytes())));

        assertTrue(SimpleHttpClient.execHttpRequest(httpPost, HttpStatus.SC_BAD_REQUEST,
                "{\"failureReason\":\"<callingNumber: Invalid>\"}",
                (String) null, (String) null));
    }

    @Test
    public void testSetLanguageMissingCallId() throws IOException, InterruptedException {}

    @Test
    public void testSetLanguageInvalidCallId() throws IOException, InterruptedException {}

    @Test
    public void testSetLanguageMissingLanguageLocationCode() throws IOException, InterruptedException {}

    @Test
    public void testSetLanguageInvalidLanguageLocationCode() throws IOException, InterruptedException {}
}
