package org.mifos.connector.ams.interop;

import static org.apache.camel.Exchange.HTTP_METHOD;
import static org.apache.camel.Exchange.HTTP_PATH;
import static org.mifos.connector.ams.camel.config.CamelProperties.TRANSFER_ACTION;
import static org.mifos.connector.ams.camel.cxfrs.HeaderBasedInterceptor.CXF_TRACE_HEADER;
import static org.mifos.connector.ams.zeebe.ZeebeVariables.ACCOUNT_NUMBER;
import static org.mifos.connector.ams.zeebe.ZeebeVariables.PARTY_ID;
import static org.mifos.connector.ams.zeebe.ZeebeVariables.PARTY_ID_TYPE;
import static org.mifos.connector.ams.zeebe.ZeebeVariables.TENANT_ID;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.camel.Exchange;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.mifos.connector.ams.camel.cxfrs.CxfrsUtil;
import org.mifos.connector.ams.tenant.TenantService;
import org.mifos.connector.ams.utils.LoanDisbursementRequestDto;
import org.mifos.connector.ams.utils.RestTemplateUtil;
import org.mifos.connector.common.channel.dto.TransactionChannelRequestDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
// @ConditionalOnExpression("${ams.local.enabled}")
public class AmsCommonService {

    @Value("${ams.local.interop.host}")
    private String amsInteropHostPath;

    @Value("${ams.local.interop.quotes-path}")
    private String amsInteropQuotesPath;

    @Value("${ams.local.interop.parties-path}")
    private String amsInteropPartiesPath;

    @Value("${ams.local.interop.transfers-path}")
    private String amsInteropTransfersPath;

    @Value("${ams.local.loan.repayment-path}")
    private String amsLoanRepaymentPath;

    @Value("${ams.local.interop.disbursal-transaction-path}")
    private String amsInteropDisbursalTransactionPath;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private CxfrsUtil cxfrsUtil;
    @Autowired
    RestTemplate restTemplate;

    @Value("${ams.local.enabled}")
    private boolean isAmsLocalEnabled;

    @Value("${mock-service.local.loan.repayment-path}")
    private String mockServiceLoanRepaymentPath;
    @Value("${mock-service.local.interop.transfers-path}")
    private String mockServiceInteropTransfersPath;
    @Value("${mock-service.local.interop.parties-path}")
    private String mockServiceAmsInteropPartiesPath;
    @Value("${mock-service.local.loan.disbursal-transaction-path}")
    private String mockServiceDisbursalTransactionPath;

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private static final String APPLICATION_TYPE = "application/json";

    public void getLocalQuote(Exchange e) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(CXF_TRACE_HEADER, true);
        headers.put(HTTP_METHOD, "POST");
        headers.put(HTTP_PATH, amsInteropQuotesPath);
        headers.put("Content-Type", "application/json");
        headers.putAll(tenantService.getHeaders(e.getProperty(TENANT_ID, String.class)));
        cxfrsUtil.sendInOut("cxfrs:bean:ams.local.interop", e, headers, e.getIn().getBody());
    }

    public void getExternalAccount(Exchange e) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(CXF_TRACE_HEADER, true);
        headers.put(HTTP_METHOD, "GET");
        logger.debug(":{}", e.getProperty(PARTY_ID_TYPE, String.class));
        logger.debug(":{}", e.getProperty(PARTY_ID, String.class));
        headers.put(HTTP_PATH, amsInteropPartiesPath.replace("{idType}", e.getProperty(PARTY_ID_TYPE, String.class)).replace("{idValue}",
                e.getProperty(PARTY_ID, String.class)));
        headers.putAll(tenantService.getHeaders(e.getProperty(TENANT_ID, String.class)));
        if (isAmsLocalEnabled) {
            cxfrsUtil.sendInOut("cxfrs:bean:ams.local.interop", e, headers, null);
        } else {
            logger.info("-------------- Calling Mock external Account API --------------");
            headers.put(HTTP_PATH, mockServiceAmsInteropPartiesPath);
            cxfrsUtil.sendInOut("cxfrs:bean:mock-service.local.interop", e, headers, null);
        }
        // cxfrsUtil.sendInOut("cxfrs:bean:ams.local.interop", e, headers, null);
    }

    public void sendTransfer(Exchange e) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(CXF_TRACE_HEADER, true);
        headers.put(HTTP_METHOD, "POST");
        headers.put(HTTP_PATH, amsInteropTransfersPath);
        logger.info("Send Transfer Body: {}", e.getIn().getBody());
        Map<String, String> queryMap = new LinkedHashMap<>();
        queryMap.put("action", e.getProperty(TRANSFER_ACTION, String.class));
        headers.put(CxfConstants.CAMEL_CXF_RS_QUERY_MAP, queryMap);
        headers.put("Content-Type", "application/json");
        headers.putAll(tenantService.getHeaders(e.getProperty(TENANT_ID, String.class)));
        if (isAmsLocalEnabled) {
            cxfrsUtil.sendInOut("cxfrs:bean:ams.local.interop", e, headers, e.getIn().getBody());
        } else {
            logger.info("-------------- Calling Mock transfers APIs --------------");
            headers.put(HTTP_PATH, mockServiceInteropTransfersPath);
            cxfrsUtil.sendInOut("cxfrs:bean:mock-service.local.interop", e, headers, e.getIn().getBody().toString());
        }
    }

    public void repayLoan(Exchange e) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(CXF_TRACE_HEADER, true);
        headers.put(HTTP_METHOD, "POST");
        headers.put(HTTP_PATH, amsLoanRepaymentPath.replace("{accountNumber}", e.getProperty(ACCOUNT_NUMBER, String.class)));
        logger.debug("Loan Repayment Body: {}", e.getIn().getBody());
        headers.put("Content-Type", APPLICATION_TYPE);
        headers.putAll(tenantService.getHeaders(e.getProperty(TENANT_ID, String.class)));
        if (isAmsLocalEnabled) {
            cxfrsUtil.sendInOut("cxfrs:bean:ams.local.loan", e, headers, e.getIn().getBody());
        } else {
            logger.info("-------------- Calling Mock Loan repayment APIs --------------");
            headers.put(HTTP_PATH, mockServiceLoanRepaymentPath);
            cxfrsUtil.sendInOut("cxfrs:bean:mock-service.local.loan", e, headers, e.getIn().getBody().toString());
        }
        // cxfrsUtil.sendInOut("cxfrs:bean:ams.local.loan", e, headers, e.getIn().getBody());
    }

    public String disburseLoan(String fineractTenantId, LoanDisbursementRequestDto loanDisbursementRequestDto, String channelRequest,
            String basicAuthHeader) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        headers.add("fineract-platform-tenantid", fineractTenantId);
        headers.add("Authorization", basicAuthHeader);
        ObjectMapper objectMapper = new ObjectMapper();
        String requestBody;
        TransactionChannelRequestDTO channelRequestDTO;
        try {
            requestBody = objectMapper.writeValueAsString(loanDisbursementRequestDto);
            channelRequestDTO = objectMapper.readValue(channelRequest, TransactionChannelRequestDTO.class);
        } catch (Exception ex) {
            logger.info(ex.getMessage());
            return null;
        }
        String accountId = channelRequestDTO.getPayer().getPartyIdInfo().getPartyIdentifier();
        String url = amsInteropHostPath + amsInteropDisbursalTransactionPath.replace("{accountId}", accountId);
        try {
            RestTemplateUtil restTemplateUtil = new RestTemplateUtil();
            ResponseEntity<String> exchange = restTemplateUtil.exchange(url, HttpMethod.POST, headers, requestBody);
            logger.info(exchange.toString());
            logger.info("Response: {} status: {}", exchange.getBody().toString(), exchange.getStatusCode());
            return exchange.getBody();
        } catch (Exception ex) {
            logger.error(ex.getMessage());
            return null;
        }
    }

    public boolean sendCallback(String callbackURL, String body) {
        logger.info("Sending Callback...");
        ResponseEntity responseEntity;
        try {
            responseEntity = restTemplate.postForEntity(callbackURL, body, null);
            if (responseEntity.getStatusCode().is2xxSuccessful()) {
                logger.info("Callback sent");
                return true;
            } else {
                logger.info("Callback failed!!!");
                return false;
            }
        } catch (Exception exception) {
            logger.info("Callback failed!!!");
            logger.debug(exception.getMessage());
        }
        return false;
    }

    public void registerInteropIdentifier(Exchange e) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(CXF_TRACE_HEADER, true);
        headers.put(HTTP_METHOD, "POST");
        headers.put(HTTP_PATH, amsInteropPartiesPath.replace("{idType}", e.getProperty(PARTY_ID_TYPE, String.class)).replace("{idValue}",
                e.getProperty(PARTY_ID, String.class)));
        headers.put("Content-Type", "application/json");
        headers.putAll(tenantService.getHeaders(e.getProperty(TENANT_ID, String.class)));
        cxfrsUtil.sendInOut("cxfrs:bean:ams.local.interop", e, headers, e.getIn().getBody());
    }

    public void removeInteropIdentifier(Exchange e) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(CXF_TRACE_HEADER, true);
        headers.put(HTTP_METHOD, "DELETE");
        headers.put(HTTP_PATH, amsInteropPartiesPath.replace("{idType}", e.getProperty(PARTY_ID_TYPE, String.class)).replace("{idValue}",
                e.getProperty(PARTY_ID, String.class)));
        headers.put("Content-Type", "application/json");
        headers.putAll(tenantService.getHeaders(e.getProperty(TENANT_ID, String.class)));
        e.getIn().setBody(null);
        cxfrsUtil.sendInOut("cxfrs:bean:ams.local.interop", e, headers, null);
    }
}
