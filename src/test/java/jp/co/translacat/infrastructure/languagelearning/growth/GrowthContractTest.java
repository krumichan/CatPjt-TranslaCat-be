package jp.co.translacat.infrastructure.languagelearning.growth;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jp.co.translacat.domain.languagelearning.growth.model.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GrowthContractTest {
    private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    private GrowthEnvelope envelope() throws Exception {try(var input=getClass().getResourceAsStream("/contracts/growth-v1-envelope.json")){return mapper.readValue(Objects.requireNonNull(input),GrowthEnvelope.class);}}
    @Test void sharedLlFixtureBindsUtf8HashAndAllOperationContracts() throws Exception {
        var e=envelope();assertEquals(e.payloadSha256(),GrowthOutboxStore.hash(e.payloadJson()));
        var ops=mapper.readTree(e.payloadJson()).get("operations");assertEquals(6,ops.size());
        for(var op:ops) GrowthOperationValidator.validate(mapper.treeToValue(op,GrowthOperation.class),mapper);
        assertEquals("EXPRESSIVENESS",ops.get(5).path("payload").path("metrics").get(0).path("metricType").asText());
    }
    @Test void unexpectedFieldsInvalidKindsAndOutOfRangeScoresAreRejected() throws Exception {
        var root=mapper.readTree(envelope().payloadJson()).get("operations");
        ObjectNode unknown=root.get(1).deepCopy();unknown.withObject("/payload").put("injected","not-allowed");
        assertThrows(IllegalArgumentException.class,()->GrowthOperationValidator.validate(mapper.treeToValue(unknown,GrowthOperation.class),mapper));
        ObjectNode score=root.get(1).deepCopy();score.withObject("/payload").putArray("scores").add(101).add(80).add(80).add(80).add(80);
        assertThrows(IllegalArgumentException.class,()->GrowthOperationValidator.validate(mapper.treeToValue(score,GrowthOperation.class),mapper));
        assertThrows(IllegalArgumentException.class,()->GrowthOperationValidator.validate(new GrowthOperation("bad","UNKNOWN",Map.of()),mapper));
    }
    @Test void coachingCannotBecomeOfficialScore() throws Exception {
        ObjectNode op=mapper.readTree(envelope().payloadJson()).path("operations").get(5).deepCopy();op.withObject("/payload").put("resultKind","SESSION_COACHING");
        assertThrows(IllegalArgumentException.class,()->GrowthOperationValidator.validate(mapper.treeToValue(op,GrowthOperation.class),mapper));
    }
    @Test void ackRequiresAllIdentityFieldsAndRecognizedOutcome() throws Exception {
        var e=envelope();assertTrue(new GrowthAcknowledgement(e.sourceInstanceId(),e.eventId(),e.userId(),e.sequence(),e.payloadSha256(),"APPLIED").matches(e));
        assertTrue(new GrowthAcknowledgement(e.sourceInstanceId(),e.eventId(),e.userId(),e.sequence(),e.payloadSha256(),"DUPLICATE").matches(e));
        assertFalse(new GrowthAcknowledgement(e.sourceInstanceId(),e.eventId(),e.userId(),e.sequence(),e.payloadSha256(),"RECORDED").matches(e));
        assertFalse(new GrowthAcknowledgement(e.sourceInstanceId(),e.eventId(),e.userId()+1,e.sequence(),e.payloadSha256(),"APPLIED").matches(e));
    }
    @Test void logsDoNotPrintGrowthPayload() throws Exception {var e=envelope();assertFalse(e.toString().contains("여행"));assertFalse(new GrowthOperation("signal:1","SIGNALS_TOUCHED",Map.of("secret","marker")).toString().contains("marker"));}
}
