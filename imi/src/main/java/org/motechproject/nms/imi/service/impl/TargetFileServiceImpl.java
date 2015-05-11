package org.motechproject.nms.imi.service.impl;


import org.apache.commons.codec.binary.Hex;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.motechproject.alerts.contract.AlertService;
import org.motechproject.alerts.domain.AlertStatus;
import org.motechproject.alerts.domain.AlertType;
import org.motechproject.event.MotechEvent;
import org.motechproject.event.listener.annotations.MotechListener;
import org.motechproject.mds.query.QueryParams;
import org.motechproject.nms.imi.domain.AuditRecord;
import org.motechproject.nms.imi.domain.CallRetry;
import org.motechproject.nms.imi.domain.FileProcessedStatus;
import org.motechproject.nms.imi.domain.FileType;
import org.motechproject.nms.imi.repository.AuditDataService;
import org.motechproject.nms.imi.repository.CallRetryDataService;
import org.motechproject.nms.imi.service.RequestId;
import org.motechproject.nms.imi.service.TargetFileNotification;
import org.motechproject.nms.imi.service.TargetFileService;
import org.motechproject.nms.imi.web.contract.FileProcessedStatusRequest;
import org.motechproject.nms.kilkari.domain.Subscriber;
import org.motechproject.nms.kilkari.domain.Subscription;
import org.motechproject.nms.kilkari.repository.SubscriberDataService;
import org.motechproject.nms.kilkari.service.SubscriptionService;
import org.motechproject.nms.props.domain.DayOfTheWeek;
import org.motechproject.nms.region.domain.LanguageLocation;
import org.motechproject.scheduler.contract.RepeatingSchedulableJob;
import org.motechproject.scheduler.service.MotechSchedulerService;
import org.motechproject.server.config.SettingsFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.UUID;

@Service("targetFileService")
public class TargetFileServiceImpl implements TargetFileService {
    private static final String TARGET_FILE_TIME = "imi.target_file_time";
    private static final String MAX_QUERY_BLOCK = "imi.max_query_block";
    private static final String TARGET_FILE_MS_INTERVAL = "imi.target_file_ms_interval";
    private static final String TARGET_FILE_DIRECTORY = "imi.target_file_directory";
    private static final String TARGET_FILE_NOTIFICATION_URL = "imi.target_file_notification_url";
    private static final String TARGET_FILE_IMI_SERVICE_ID = "imi.target_file_imi_service_id";
    private static final String TARGET_FILE_CALL_FLOW_URL = "imi.target_file_call_flow_url";
    private static final String NORMAL_PRIORITY = "0";

    private static final String GENERATE_TARGET_FILE_EVENT = "nms.obd.generate_target_file";

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormat.forPattern("yyyyMMddHHmmss");

    private SettingsFacade settingsFacade;
    private MotechSchedulerService schedulerService;
    private AlertService alertService;
    private SubscriptionService subscriptionService;
    private SubscriberDataService subscriberDataService;
    private CallRetryDataService callRetryDataService;
    private AuditDataService auditDataService;

    private static final Logger LOGGER = LoggerFactory.getLogger(TargetFileServiceImpl.class);


    /**
     * Use the MOTECH scheduler to setup a repeating job
     * The job will start today at the time stored in imi.target_file_time in imi.properties
     * It will repeat every imi.target_file_ms_interval milliseconds (default value is a day)
     */
    private void scheduleTargetFileGeneration() {
        //Calculate today's fire time
        DateTimeFormatter fmt = DateTimeFormat.forPattern("H:m");
        String timeProp = settingsFacade.getProperty(TARGET_FILE_TIME);
        DateTime time = fmt.parseDateTime(timeProp);
        DateTime today = DateTime.now()                     // This means today's date...
                .withHourOfDay(time.getHourOfDay())         // ...at the hour...
                .withMinuteOfHour(time.getMinuteOfHour())   // ...and minute specified in imi.properties
                .withSecondOfMinute(0)
                .withMillisOfSecond(0);

        //Millisecond interval between events
        String intervalProp = settingsFacade.getProperty(TARGET_FILE_MS_INTERVAL);
        Long msInterval = Long.parseLong(intervalProp);

        LOGGER.debug(String.format("The %s message will be sent every %sms starting %s",
                GENERATE_TARGET_FILE_EVENT, msInterval.toString(), today.toString()));

        //Schedule repeating job
        MotechEvent event = new MotechEvent(GENERATE_TARGET_FILE_EVENT);
        RepeatingSchedulableJob job = new RepeatingSchedulableJob(
                event,          //MOTECH event
                today.toDate(), //startTime
                null,           //endTime, null means no end time
                null,           //repeatCount, null means infinity
                msInterval,     //repeatIntervalInMilliseconds
                true);          //ignorePastFiresAtStart
        schedulerService.safeScheduleRepeatingJob(job);
    }


    @Autowired
    public TargetFileServiceImpl(@Qualifier("imiSettings") SettingsFacade settingsFacade,
                                 MotechSchedulerService schedulerService, AlertService alertService,
                                 SubscriptionService subscriptionService,
                                 CallRetryDataService callRetryDataService,
                                 SubscriberDataService subscriberDataService,
                                 AuditDataService auditDataService) {
        this.schedulerService = schedulerService;
        this.settingsFacade = settingsFacade;
        this.alertService = alertService;
        this.subscriptionService = subscriptionService;
        this.callRetryDataService = callRetryDataService;
        this.subscriberDataService = subscriberDataService;
        this.auditDataService = auditDataService;

        scheduleTargetFileGeneration();
    }


    private String targetFileName() {
        return String.format("OBD_%s.csv", TIME_FORMATTER.print(DateTime.now()));
    }


    private void insertTargetFileAuditRecord(String fileIdentifier, TargetFileNotification tfn, String status) {
        auditDataService.create(new AuditRecord(fileIdentifier, FileType.TARGET_FILE, tfn.getFileName(), status,
                tfn.getRecordCount(), tfn.getChecksum()));
    }


    //todo: verify we can do that - if the shared directory is an FTP share this might not work
    private File createTargetFileDirectory() {
        File userHome = new File(System.getProperty("user.home"));
        File targetFileDirectory = new File(userHome, settingsFacade.getProperty(TARGET_FILE_DIRECTORY));

        if (targetFileDirectory.exists()) {
            LOGGER.info("targetFile directory exists: {}", targetFileDirectory);
        } else {
            LOGGER.info("creating targetFile directory: {}", targetFileDirectory);
            if (!targetFileDirectory.mkdirs()) {
                String error = String.format("Unable to create targetFileDirectory %s: mkdirs() failed",
                        targetFileDirectory);
                LOGGER.error(error);
                alertService.create(targetFileDirectory.toString(), "targetFileDirectory", "mkdirs() failed",
                        AlertType.CRITICAL, AlertStatus.NEW, 0, null);
                insertTargetFileAuditRecord(null, new TargetFileNotification(), error);
                throw new IllegalStateException();
            }
        }
        return targetFileDirectory;
    }


    private void writeSubscriptionRow(String requestId, String serviceId, // NO CHECKSTYLE More than 7 parameters
                                      String msisdn, String priority,  String callFlowUrl, String contentFileName,
                                      int weekId, String languageLocationCode, String circle,
                                      String subscriptionOrigin, OutputStreamWriter writer) throws IOException {
        /*
         * #1 RequestId
         *
         * A unique Request id for each obd record
         */
        writer.write(requestId);
        writer.write(",");

        /*
         * #2 ServiceId
         *
         * Unique Id provided by IMImobile for a particular service
         */
        writer.write(serviceId);
        writer.write(",");

        /*
         * #3 Msisdn
         *
         * 10 digit number to be dialed out
         */
        writer.write(msisdn);
        writer.write(",");

        /*
         * #4 Cli
         *
         * 10 Digit number to be displayed as CLI for the call. If left blank, the default CLI of the service
         * shall be picked up.
         */
        writer.write(""); // No idea why/what that field is: let's write nothing
        writer.write(",");

        /*
         * #5 Priority
         *
         * Specifies the priority with which the call is to be made. By default value is 0.
         * Possible Values: 0-Default, 1-Medium Priority, 2-High Priority
         */
        writer.write(priority); //todo: look into optimizing that especially with the retries
        writer.write(",");

        /*
         * #6 CallFlowURL
         *
         * The URL of the VXML flow. If unspecified, default VXML URL specified for the service shall be picked up
         */
        writer.write(callFlowUrl);
        writer.write(",");

        /*
         * #7 ContentFileName
         *
         * Content file to be played
         */
        //todo: call a function on subscription that returns the content file name to be played using today's date
        writer.write(contentFileName);
        writer.write(",");

        /*
         * #8 WeekId
         *
         * Week id of the messaged delivered in OBD
         */
        //todo: call a function on subscription that returns the week id name to be played using today's date
        writer.write(Integer.toString(weekId));
        writer.write(",");

        /*
         * #9 LanguageLocationCode
         *
         * To identify the language
         */
        writer.write(languageLocationCode);
        writer.write(",");

        /*
         * #10 Circle
         *
         * Circle of the beneficiary.
         */
        //todo call a function on subscriber that returns the subscriber's circle
        writer.write(circle);
        writer.write(",");

        /*
         * #11 subscription mode
         *
         * I for IVR origin, M for MCTS origin
         */
        writer.write(subscriptionOrigin);

        writer.write("\n");
    }


    private int generateFreshCalls(int maxQueryBlock, String imiServiceId, String callFlowUrl,
                                   String fileIdentifier, OutputStreamWriter writer) throws IOException {

        int recordCount = 0;
        int page = 1;
        int numBlockRecord;
        do {
            List<Subscription> subscriptions = subscriptionService.findActiveSubscriptions(page, maxQueryBlock);
            numBlockRecord = subscriptions.size();

            for (Subscription subscription : subscriptions) {

                Subscriber subscriber = subscription.getSubscriber();

                //todo: don't understand why subscriber.getLanguage() doesn't work here...
                // it's not working because of https://applab.atlassian.net/browse/MOTECH-1678
                LanguageLocation languageLocation;
                languageLocation = (LanguageLocation) subscriberDataService.getDetachedField(subscriber,
                        "languageLocation");
                writer.write(languageLocation.getCode());

                RequestId requestId = new RequestId(fileIdentifier, subscription.getSubscriptionId());
                writeSubscriptionRow(requestId.toString(), imiServiceId,
                        subscriber.getCallingNumber().toString(), NORMAL_PRIORITY, callFlowUrl,
                        "???ContentFileName???", //todo: get that from lauren when it's ready
                        1, //todo: and that too
                        languageLocation.getCode(), subscriber.getCircle(),
                        subscription.getOrigin().getCode(), writer);
            }

            page++;
            recordCount += numBlockRecord;

        } while (numBlockRecord > 0);

        return recordCount;
    }


    private int generateRetryCalls(int maxQueryBlock, String imiServiceId, String callFlowUrl,
                                   String fileIdentifier, OutputStreamWriter writer) throws IOException {

        //figure out which day to work with
        final DayOfTheWeek today = DayOfTheWeek.today();

        int recordCount = 0;
        int page = 1;
        int numBlockRecord;
        do {
            List<CallRetry> callRetries = callRetryDataService.findByDayOfTheWeek(today,
                    new QueryParams(page, maxQueryBlock));
            numBlockRecord = callRetries.size();

            for (CallRetry callRetry : callRetries) {
                RequestId requestId = new RequestId(fileIdentifier, callRetry.getSubscriptionId());
                writeSubscriptionRow(requestId.toString(), imiServiceId,
                        callRetry.getMsisdn().toString(), NORMAL_PRIORITY, callFlowUrl,
                        "???ContentFileName???", //todo: get that from lauren when it's ready
                        1, //todo: and that too
                        callRetry.getLanguageLocationCode(), callRetry.getCircle(),
                        callRetry.getSubscriptionOrigin(), writer);
            }

            page++;
            recordCount += numBlockRecord;

        } while (numBlockRecord > 0);

        return recordCount;
    }


    /*
    /**
     * 4.4.1 Target File Format
     */
    public TargetFileNotification generateTargetFile() {
        String targetFileName = targetFileName();
        File targetFileDirectory;
        MessageDigest md;
        int recordCount = 0;

        try {
            targetFileDirectory = createTargetFileDirectory();
        } catch (IllegalStateException e) {
            return null;
        }

        //generate a unique identifier for the targetFile
        String fileIdentifier = UUID.randomUUID().toString();


        File targetFile = new File(targetFileDirectory, targetFileName);
        try (FileOutputStream fos = new FileOutputStream(targetFile);
            OutputStreamWriter writer = new OutputStreamWriter(fos)) {

            md = MessageDigest.getInstance("MD5");
            @SuppressWarnings("PMD.UnusedLocalVariable")
            DigestOutputStream dos = new DigestOutputStream(fos, md);

            int maxQueryBlock = Integer.parseInt(settingsFacade.getProperty(MAX_QUERY_BLOCK));
            String imiServiceId = settingsFacade.getProperty(TARGET_FILE_IMI_SERVICE_ID);
            String callFlowUrl = settingsFacade.getProperty(TARGET_FILE_CALL_FLOW_URL);
            if (callFlowUrl == null) {
                //it's ok to have an empty call flow url - the spec says the default call flow will be used
                //whatever that is...
                callFlowUrl = "";
            }

            //FRESH calls
            recordCount = generateFreshCalls(maxQueryBlock, imiServiceId, callFlowUrl, fileIdentifier, writer);

            //Retry calls
            recordCount += generateRetryCalls(maxQueryBlock, imiServiceId, callFlowUrl, fileIdentifier, writer);

            LOGGER.info("Created targetFile with {} record{}", recordCount, recordCount == 1 ? "" : "s");

        } catch (NoSuchAlgorithmException | IOException e) {
            LOGGER.error(e.getMessage());
            alertService.create(targetFile.toString(), "targetFile", e.getMessage(), AlertType.CRITICAL,
                    AlertStatus.NEW, 0, null);
            insertTargetFileAuditRecord(null, new TargetFileNotification(targetFile.toString(), null, null),
                    e.getMessage());
            return null;
        }

        String md5Checksum = new String(Hex.encodeHex(md.digest()));
        TargetFileNotification tfn = new TargetFileNotification(targetFileName, md5Checksum, recordCount);
        LOGGER.info("TargetFileNotification = {}", tfn.toString());

        //audit the success
        insertTargetFileAuditRecord(fileIdentifier, tfn, "Success");

        return tfn;
    }


    private void sendNotificationRequest(TargetFileNotification tfn) {
        String notificationUrl = settingsFacade.getProperty(TARGET_FILE_NOTIFICATION_URL);
        LOGGER.info("Sending {} to {}", tfn, notificationUrl);

        try {
            HttpClient httpClient = HttpClients.createDefault();
            HttpPost httpPost = new HttpPost(notificationUrl);
            ObjectMapper mapper = new ObjectMapper();
            String requestJson = mapper.writeValueAsString(tfn);
            httpPost.setHeader("Content-type", "application/json");
            httpPost.setEntity(new StringEntity(requestJson));
            HttpResponse response = httpClient.execute(httpPost);
            int responseCode = response.getStatusLine().getStatusCode();
            if (responseCode != HttpStatus.SC_OK) {
                String error = String.format("Expecting HTTP 200 response from %s but received HTTP %d : %s ",
                        notificationUrl, responseCode, EntityUtils.toString(response.getEntity()));
                LOGGER.error(error);
                alertService.create("targetFile notification request", "targetFile", error, AlertType.CRITICAL,
                        AlertStatus.NEW, 0, null);
            }
        } catch (IOException e) {
            LOGGER.error("Unable to send targetFile notification request: {}", e.getMessage());
            alertService.create("targetFile notification request", "targetFile", e.getMessage(), AlertType.CRITICAL,
                    AlertStatus.NEW, 0, null);
        }
    }


    @MotechListener(subjects = { GENERATE_TARGET_FILE_EVENT })
    public void generateTargetFile(MotechEvent event) {
        LOGGER.info(event.toString());

        TargetFileNotification tfn = generateTargetFile();

        if (tfn != null) {
            //notify the IVR system the file is ready
            sendNotificationRequest(tfn);
        }
    }


    @Override
    public void handleFileProcessedStatusNotification(FileProcessedStatusRequest request) {
        if (request.getFileProcessedStatus() == FileProcessedStatus.FILE_PROCESSED_SUCCESSFULLY) {
            LOGGER.info(request.toString());
            //We're happy.
            //todo: audit that?
        } else {
            LOGGER.error(request.toString());
            alertService.create(request.getFileName(), "targetFileName", "Target File Processing Error",
                    AlertType.CRITICAL, AlertStatus.NEW, 0, null);
            //todo: audit that?
        }
    }
}
