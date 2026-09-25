package jp.co.translacat.infrastructure.languagelearning.growth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.infrastructure.languagelearning.client.LanguageLearningServiceException;
import jp.co.translacat.infrastructure.languagelearning.client.config.LanguageLearningClientProperties;
import jp.co.translacat.infrastructure.languagelearning.client.security.LanguageLearningInternalJwtProvider;
import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.time.*;
import java.util.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.junit.jupiter.api.Assertions.*;

class GrowthHttpClientTest {
    private static final String SOURCE="1ae93ac7-179b-4700-9edb-eb001461f033";
    private MockRestServiceServer server;private GrowthHttpClient client;
    private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    @BeforeEach void setup(){
        var builder=RestClient.builder().baseUrl("http://localhost:8081");server=MockRestServiceServer.bindTo(builder).build();
        var p=new LanguageLearningClientProperties();p.getInternalJwt().setSecretBase64(Base64.getEncoder().encodeToString(new byte[32]));
        client=new GrowthHttpClient(builder.build(),new LanguageLearningInternalJwtProvider(p,Clock.systemUTC()),mapper);
    }
    private String snapshot(long user,long seq,boolean preview){return "{\"userId\":"+user+",\"sourceInstanceId\":\""+SOURCE+"\",\"sequence\":"+seq+",\"preview\":"+preview+",\"profile\":null,\"masteries\":[],\"signals\":{}}";}
    @Test void readUsesUserBoundTokenAndMinimumSequence(){
        server.expect(requestTo("http://localhost:8081/internal/v1/language-learning/growth/snapshot")).andExpect(method(HttpMethod.POST)).andExpect(request->{
            String token=Objects.requireNonNull(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).substring(7);
            var claims=mapper.readTree(Base64.getUrlDecoder().decode(token.split("\\.")[1]));assertEquals("123",claims.path("sub").asText());assertEquals("ll-internal",claims.path("tokenUse").asText());
        }).andRespond(withSuccess(snapshot(123,2,false),MediaType.APPLICATION_JSON));
        assertEquals(2,client.snapshot(123,SOURCE,2,List.of(),null).sequence());server.verify();
    }
    @Test void staleOrOtherUserResponseIsNotReturnedAsNormal(){
        server.expect(requestTo("http://localhost:8081/internal/v1/language-learning/growth/snapshot")).andRespond(withSuccess(snapshot(456,2,false),MediaType.APPLICATION_JSON));
        assertEquals("LL_GROWTH_CONTRACT_ERROR",assertThrows(LanguageLearningServiceException.class,()->client.snapshot(123,SOURCE,2,List.of(),null)).getErrorCode());
    }
    @Test void pendingHasNoLocalProfileFallback(){
        server.expect(requestTo("http://localhost:8081/internal/v1/language-learning/growth/snapshot")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("{\"code\":\"GROWTH_SYNC_PENDING\"}").contentType(MediaType.APPLICATION_JSON));
        var error=assertThrows(LanguageLearningServiceException.class,()->client.snapshot(123,SOURCE,2,List.of(),null));assertEquals("GROWTH_SYNC_PENDING",error.getErrorCode());assertEquals(HttpStatus.SERVICE_UNAVAILABLE,error.getStatus());
    }
    @Test void deliveryUsesNarrowServiceToken() throws Exception {
        GrowthEnvelope e;try(var input=getClass().getResourceAsStream("/contracts/growth-v1-envelope.json")){e=mapper.readValue(input,GrowthEnvelope.class);}
        var ack=new GrowthAcknowledgement(SOURCE,e.eventId(),123,1,e.payloadSha256(),"APPLIED");
        server.expect(requestTo("http://localhost:8081/internal/v1/service/language-learning/growth/commands")).andExpect(request->{
            String token=Objects.requireNonNull(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).substring(7);
            var claims=mapper.readTree(Base64.getUrlDecoder().decode(token.split("\\.")[1]));assertEquals("ll-growth-v1",claims.path("tokenUse").asText());assertEquals("growth:write",claims.path("scopes").get(0).asText());assertFalse(claims.has("roles"));
        }).andRespond(withSuccess(mapper.writeValueAsString(ack),MediaType.APPLICATION_JSON));
        assertTrue(client.deliver(e).matches(e));server.verify();
    }
}
