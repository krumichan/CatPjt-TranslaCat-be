package jp.co.translacat.domain.accountbook.transaction.facade;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jp.co.translacat.domain.accountbook.accountbook.entity.AccountBook;
import jp.co.translacat.domain.accountbook.accountbook.service.AccountBookAccessService;
import jp.co.translacat.domain.accountbook.category.entity.AccountBookCategory;
import jp.co.translacat.domain.accountbook.category.repository.AccountBookCategoryRepository;
import jp.co.translacat.domain.accountbook.receiptkeyword.repository.ReceiptKeywordRepository;
import jp.co.translacat.domain.accountbook.receiptkeyword.service.ReceiptAnalysisOptionQueryService;
import jp.co.translacat.domain.accountbook.transaction.service.ReceiptConversionService;
import jp.co.translacat.domain.currency.entity.Currency;
import jp.co.translacat.domain.currency.entity.ExchangeRate;
import jp.co.translacat.domain.currency.service.*;
import jp.co.translacat.infrastructure.client.ai.server.AiServerClient;
import jp.co.translacat.infrastructure.client.ai.server.dto.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class ReceiptAnalysisFacadeTest {
    final AccountBookAccessService access=mock(AccountBookAccessService.class);
    final ReceiptKeywordRepository keywords=mock(ReceiptKeywordRepository.class);
    final AccountBookCategoryRepository categories=mock(AccountBookCategoryRepository.class);
    final ReceiptAnalysisOptionQueryService options=new ReceiptAnalysisOptionQueryService(keywords,categories);
    final AiServerClient ai=mock(AiServerClient.class);
    final ExchangeRateService rates=mock(ExchangeRateService.class);
    final AccountBookReceiptAnalysisFacade facade=new AccountBookReceiptAnalysisFacade(access,options,ai,new ReceiptConversionService(rates));
    final AccountBook book=AccountBook.create(null,Currency.create("JPY","Yen","Y",0,false),"Test","Test");
    final ObjectMapper mapper=JsonMapper.builder().addModule(new JavaTimeModule()).build();
    final LocalDate date=LocalDate.of(2026,9,12);
    ReceiptAnalysisFacadeTest() {
        when(access.getAccessibleAccountBook(1L,2L)).thenReturn(book);
        when(keywords.findByCurrencyCodeIsNullAndEnabledTrueAndDeletedFalseOrderByDisplayOrderAscIdAsc()).thenReturn(List.of());
        when(categories.findByAccountBookIdAndActiveTrueOrderByDisplayOrderAscNameAsc(1L)).thenReturn(List.of(AccountBookCategory.create(book,"Food",1)));
        when(rates.getRate("USD","JPY",date)).thenReturn(ExchangeRate.create("USD","JPY",new BigDecimal("150"),date,date,"TEST"));
        when(rates.getRate("JPY","JPY",date)).thenReturn(ExchangeRate.create("JPY","JPY",BigDecimal.ONE,date,date,"IDENTITY"));
        when(rates.getRate("EUR","JPY",date)).thenThrow(new RateUnavailableException());
    }
    @Test void papasuPaymentFactsAreRecalculatedTo5020ByBackend() throws Exception {
        var aiResponse=mapper.readValue("""
            {"receipts":[{"receipt_id":"papasu","title":"どらっぐ ぱぱす 船堀店",
            "store_name":"どらっぐ ぱぱす","branch_name":"船堀店","purchase_total":"7089",
            "payment_breakdown":[
              {"payment_type":"LOYALTY_POINTS","amount":"2069","evidence":"ポイント支払","duplicate_group":null},
              {"payment_type":"CREDIT_CARD","amount":"5020","evidence":"クレジット","duplicate_group":"card-1"},
              {"payment_type":"CREDIT_CARD","amount":"5020","evidence":"カード明細","duplicate_group":"card-1"}],
            "change":"0","book_amount":"7089","original_amount":"7089","detected_currency_code":"JPY",
            "transaction_date":"2026-09-12","category_name":"Food","confidence":0.9,
            "status":"READY"}],"receipt_count":1,"warnings":[],"ocr_engine":"vision","used_ai":true}
            """,AiReceiptAnalysisResponse.class);
        when(ai.callReceiptAnalysis(any(),any())).thenReturn(aiResponse);
        var result=facade.analyze(1L,2L,null,"VISION_ONLY").receipts().getFirst();
        assertThat(result.purchaseTotal()).isEqualByComparingTo("7089");
        assertThat(result.bookAmount()).isEqualByComparingTo("5020");
        assertThat(result.originalAmount()).isEqualByComparingTo("5020");
        assertThat(result.convertedAmount()).isEqualByComparingTo("5020");
        assertThat(result.warnings()).contains("AI_BOOK_AMOUNT_DISAGREED","DUPLICATE_PAYMENT_DETAIL_COLLAPSED");
        assertThat(result.status()).isEqualTo("READY");
    }
    AiReceiptAnalysisResponse.Item item(String id,String currency,String amount,String date,String category) {
        return new AiReceiptAnalysisResponse.Item(id,"Purchase","Store",amount,currency,date,category,null,.9,"en","READY",List.of());
    }
    @Test void mixedCurrencyPartialFailureKeepsSuccessfulReceipt() {
        when(ai.callReceiptAnalysis(any(),any())).thenReturn(new AiReceiptAnalysisResponse(List.of(
                item("one","USD","12.34",date.toString(),"Food"),item("two","EUR","10.25",date.toString(),"Food")),2,List.of(),"vision",true));
        var result=facade.analyze(1L,2L,null,null);
        assertThat(result.receiptCount()).isEqualTo(2);
        assertThat(result.receipts().getFirst().convertedAmount()).isEqualByComparingTo("1851");
        assertThat(result.receipts().get(1).conversionStatus()).isEqualTo("RATE_UNAVAILABLE");
        var captor=org.mockito.ArgumentCaptor.forClass(AiReceiptAnalysisOptions.class);
        verify(ai).callReceiptAnalysis(any(),captor.capture());
        assertThat(captor.getValue().categoryCandidates()).containsExactly("Food");
        assertThat(captor.getValue().defaultCategoryCandidates())
                .contains("식비", "생활", "의료", "기타");
        assertThat(captor.getValue().ocrLanguage()).isNull();
        assertThat(captor.getValue().analysisMode()).isEqualTo("VISION_FIRST");
        verify(categories, never()).save(any());
    }
    @Test void malformedAmountsAndDatesRemainReviewableWhileSafeNewCategorySurvives() {
        when(ai.callReceiptAnalysis(any(),any())).thenReturn(new AiReceiptAnalysisResponse(List.of(
                item("one","USD","not-money","2026-02-30","invented"),
                item("two","USD","12.34",date.toString(),"Food")),2,List.of(),"vision",true));
        var result=facade.analyze(1L,2L,null,"VISION_ONLY");
        assertThat(result.receipts()).hasSize(2);
        assertThat(result.receipts().getFirst().warnings()).contains("INVALID_AMOUNT","INVALID_DATE","CATEGORY_NEW_SUGGESTION");
        assertThat(result.receipts().getFirst().categoryName()).isEqualTo("invented");
        assertThat(result.receipts().getFirst().categorySource()).isEqualTo("NEW");
        assertThat(result.receipts().get(1).status()).isEqualTo("READY");
    }
    @Test void emptyActiveCategoryCandidatesAreReportedSeparatelyFromAiClassification() {
        when(categories.findByAccountBookIdAndActiveTrueOrderByDisplayOrderAscNameAsc(1L))
                .thenReturn(List.of());
        when(ai.callReceiptAnalysis(any(),any())).thenReturn(new AiReceiptAnalysisResponse(List.of(
                item("one","USD","12.34",date.toString(),null)),1,List.of(),"vision",true));
        var result=facade.analyze(1L,2L,null,"VISION_FIRST");
        assertThat(result.warnings()).contains("CATEGORY_CANDIDATES_EMPTY");
        assertThat(result.receipts().getFirst().categoryName()).isEqualTo("기타");
        assertThat(result.receipts().getFirst().categorySource()).isEqualTo("FALLBACK");
        assertThat(result.receipts().getFirst().warnings()).contains("CATEGORY_FALLBACK_USED");
    }
    @Test void nullUnreadableItemAndDuplicateIdRemainDistinctReviewCandidates() {
        var invalid=new AiReceiptAnalysisResponse.Item("same","Purchase",null,"10","USD",date.toString(),"Food",null,8.0,"en","READY",List.of());
        when(ai.callReceiptAnalysis(any(),any())).thenReturn(new AiReceiptAnalysisResponse(Arrays.asList(
                invalid,item("same","USD","12",date.toString(),"Food"),null),3,null,"ocr",false));
        var result=facade.analyze(1L,2L,null,"OCR_ONLY");
        assertThat(result.receipts()).hasSize(3);
        assertThat(result.receipts().stream().map(i -> i.receiptId()).distinct()).hasSize(3);
        assertThat(result.receipts().getFirst().confidence()).isNull();
        assertThat(result.receipts().get(2).status()).isEqualTo("UNREADABLE");
    }
    @Test void decimalStringsAndSnakeCaseContractSerializeWithoutRawText() throws Exception {
        var aiResponse=mapper.readValue("""
            {"receipts":[{"receipt_id":"one","title":"Coffee","store_name":"Cafe",
            "original_amount":"10.125","detected_currency_code":"USD","transaction_date":"2026-09-12",
            "category_name":"Food","confidence":0.9,"status":"READY","currency_confidence":0.95}],
            "receipt_count":1,"warnings":[],"ocr_engine":"vision","used_ai":true,
            "analysis_trace_id":"trace-contract-123"}
            """,AiReceiptAnalysisResponse.class);
        when(ai.callReceiptAnalysis(any(),any())).thenReturn(aiResponse);
        var json=mapper.readTree(mapper.writeValueAsString(facade.analyze(1L,2L,null,"VISION_FIRST")));
        assertThat(json.path("receipts").get(0).path("originalAmount").isTextual()).isTrue();
        assertThat(json.path("receipts").get(0).path("convertedAmount").isTextual()).isTrue();
        assertThat(json.path("analysisTraceId").asText()).isEqualTo("trace-contract-123");
        assertThat(json.has("rawText")).isFalse();
        assertThat(mapper.writeValueAsString(options.getOptions(1L))).doesNotContain("currency_code");
    }
    @Test void oversizedResponseHasExplicitTruncationWarning() {
        var items = java.util.stream.IntStream.range(0, 31)
                .mapToObj(i -> item("r" + i, "USD", "10", date.toString(), "Food")).toList();
        when(ai.callReceiptAnalysis(any(),any())).thenReturn(new AiReceiptAnalysisResponse(items,31,List.of(),"vision",true));
        var result = facade.analyze(1L,2L,null,"VISION_FIRST");
        assertThat(result.receiptCount()).isEqualTo(30);
        assertThat(result.warnings()).contains("RECEIPT_LIMIT_REACHED");
    }
    @ParameterizedTest
    @ValueSource(strings = {"1E+50000000", "1E-50000000"})
    void malformedModelExponentCannotExpandIntoTheWirePayload(String amount) throws Exception {
        when(ai.callReceiptAnalysis(any(),any())).thenReturn(new AiReceiptAnalysisResponse(List.of(
                item("bad","USD",amount,date.toString(),"Food"),
                item("good","USD","10",date.toString(),"Food")),2,List.of(),"vision",true));
        var result=facade.analyze(1L,2L,null,"VISION_FIRST");
        assertThat(result.receipts().getFirst().originalAmount()).isNull();
        assertThat(result.receipts().getFirst().warnings()).contains("INVALID_AMOUNT");
        assertThat(result.receipts().get(1).convertedAmount()).isEqualByComparingTo("1500");
        assertThat(mapper.writeValueAsString(result)).hasSizeLessThan(4096).doesNotContain("50000000");
    }
}
