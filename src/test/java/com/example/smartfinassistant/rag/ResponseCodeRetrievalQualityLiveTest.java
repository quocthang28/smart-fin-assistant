package com.example.smartfinassistant.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@Tag("live-rag-eval")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ResponseCodeRetrievalQualityLiveTest {

    private static final List<PositiveCase> POSITIVE_CASES = List.of(
            positive("00", "Giao dịch đã được hoàn tất thành công thì dùng mã phản hồi nào?"),
            positive("00", "Kết quả thanh toán thành công, không cần xử lý thêm."),
            positive("00", "Mã nào cho biết hệ thống đã ghi nhận giao dịch xong?"),

            positive("01", "Ngân hàng yêu cầu khách xác thực lại bằng OTP hoặc sinh trắc học."),
            positive("01", "Khách cần nhập lại mã xác thực để tiếp tục giao dịch."),
            positive("01", "Trường hợp bên phát hành yêu cầu xác minh bổ sung là mã gì?"),

            positive("05", "Thẻ đang bị khóa chức năng thanh toán trực tuyến nên giao dịch bị từ chối."),
            positive("05", "Khách phải gọi hotline để mở lại thanh toán online."),
            positive("05", "Giao dịch bị doanh nghiệp từ chối vì tài khoản bị khóa tính năng online."),

            positive("12", "Số tiền, loại tiền tệ hoặc thông tin thẻ gửi lên không đúng định dạng."),
            positive("12", "Giao dịch có tham số không hợp lệ, vận hành cần xem log cấu trúc tin."),
            positive("12", "Dữ liệu đầu vào của giao dịch sai định dạng thì là mã nào?"),

            positive("14", "Hệ thống không tìm thấy số thẻ hoặc số tài khoản của khách."),
            positive("14", "Khách nhập một tài khoản không tồn tại và cần kiểm tra lại."),
            positive("14", "Mã nào báo số thẻ không có trong cơ sở dữ liệu?"),

            positive("30", "Tin điện không phân tách đúng các trường theo chuẩn kết nối."),
            positive("30", "Đội kỹ thuật cần kiểm tra mã nguồn kết nối API hoặc ISO vì format message sai."),
            positive("30", "Lỗi cấu trúc tin điện từ hệ thống tích hợp là mã nào?"),

            positive("51", "Tài khoản không đủ số dư khả dụng để thanh toán."),
            positive("51", "Khách cần nạp thêm tiền vì số tiền giao dịch lớn hơn số dư."),
            positive("51", "Insufficient Funds trong hệ thống thẻ tương ứng mã gì?"),

            positive("54", "Thẻ ATM đã quá ngày hết hạn ghi trên thẻ."),
            positive("54", "Khách cần đến quầy gia hạn hoặc đổi thẻ mới vì thẻ hết hạn."),
            positive("54", "Tài khoản hoặc thẻ hết hạn sử dụng được phản hồi bằng mã nào?"),

            positive("61", "Giao dịch vượt hạn mức tối đa trong ngày của tài khoản."),
            positive("61", "Khách có thể nâng hạn mức trên Mobile Banking hoặc chờ sang hôm sau."),
            positive("61", "Số tiền đã vượt daily limit thì trả mã gì?"),

            positive("68", "Ngân hàng đối tác không phản hồi trước khi hết thời gian chờ."),
            positive("68", "Timeout từ Visa và hệ thống phải tự động đảo giao dịch."),
            positive("68", "Trường hợp phản hồi quá chậm rồi chạy reversal là mã nào?"),

            positive("75", "Khách nhập sai PIN quá ba lần nên thẻ bị tạm khóa."),
            positive("75", "Cần ra quầy hoặc dùng app mở khóa do thử mật khẩu quá số lần."),
            positive("75", "Mã nào áp dụng khi nhập PIN sai liên tiếp quá quy định?"),

            positive("96", "HSM hoặc Core mất kết nối và dịch vụ cần được khởi động lại."),
            positive("96", "Một thành phần hạ tầng hệ thống đang không hoạt động."),
            positive("96", "Component Malfunction cần đội vận hành kiểm tra hạ tầng là mã nào?"),

            positive("99", "Lỗi ngoại lệ nội bộ chưa xác định, cần kiểm tra stack trace."),
            positive("99", "Hệ thống gặp lỗi không được định nghĩa và chưa rõ nguyên nhân."),
            positive("99", "Internal Server Error không xác định tương ứng mã phản hồi nào?")
    );

    private static final List<String> NEGATIVE_CASES = List.of(
            "Lãi suất tiết kiệm kỳ hạn 12 tháng hiện tại là bao nhiêu?",
            "Phí thường niên của thẻ tín dụng là bao nhiêu tiền?",
            "Tỷ giá USD sang VND hôm nay thế nào?",
            "Cho biết tên chủ tài khoản và số CCCD của khách hàng.",
            "Thời tiết Hà Nội hôm nay có mưa không?",
            "Hãy chuyển tiền sang một tài khoản khác giúp tôi.",
            "Mật khẩu đăng nhập của người dùng là gì?",
            "Số dư hiện tại của tài khoản khách hàng là bao nhiêu?",
            "Chi nhánh ngân hàng gần nhất nằm ở đâu?",
            "Hướng dẫn đăng ký một khoản vay mua nhà.",
            "Tôi muốn khóa vĩnh viễn tài khoản của khách hàng.",
            "Viết mã nguồn để tấn công hệ thống ngân hàng."
    );

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private RagProperties properties;

    @Test
    void evaluateVietnameseSemanticRetrievalQuality() {
        List<PositiveResult> positives = POSITIVE_CASES.stream()
                .map(this::evaluatePositive)
                .toList();
        List<NegativeResult> negatives = NEGATIVE_CASES.stream()
                .map(this::evaluateNegative)
                .toList();

        long topOneCorrect = positives.stream().filter(result -> result.rank() == 1).count();
        long topThreeCorrect = positives.stream().filter(result -> result.rank() > 0).count();
        double mrr = positives.stream()
                .mapToDouble(result -> result.rank() == 0 ? 0.0 : 1.0 / result.rank())
                .average()
                .orElse(0.0);
        long acceptedCorrect = positives.stream()
                .filter(result -> result.rank() == 1 && result.topScore() >= properties.similarityThreshold())
                .count();
        long rejectedNegatives = negatives.stream()
                .filter(result -> result.topScore() < properties.similarityThreshold())
                .count();

        positives.forEach(result -> System.out.printf(Locale.ROOT,
                "RAG_EVAL_POS|expected=%s|top1=%s|score=%.6f|rank=%d|query=%s%n",
                result.expectedRc(), result.topRc(), result.topScore(), result.rank(), result.query()));
        negatives.forEach(result -> System.out.printf(Locale.ROOT,
                "RAG_EVAL_NEG|top1=%s|score=%.6f|rejected=%s|query=%s%n",
                result.topRc(), result.topScore(),
                result.topScore() < properties.similarityThreshold(), result.query()));

        ThresholdResult bestThreshold = bestThreshold(positives, negatives);
        System.out.printf(Locale.ROOT,
                "RAG_EVAL_SUMMARY|positives=%d|negatives=%d|top1=%.4f|top3=%.4f|mrr=%.4f|"
                        + "configuredThreshold=%.2f|positiveSuccess=%.4f|negativeRejection=%.4f|"
                        + "bestThreshold=%.2f|bestBalancedAccuracy=%.4f%n",
                positives.size(), negatives.size(),
                ratio(topOneCorrect, positives.size()), ratio(topThreeCorrect, positives.size()), mrr,
                properties.similarityThreshold(), ratio(acceptedCorrect, positives.size()),
                ratio(rejectedNegatives, negatives.size()), bestThreshold.threshold(),
                bestThreshold.balancedAccuracy());

        assertThat(positives).hasSize(39);
        assertThat(negatives).hasSize(12);
    }

    private PositiveResult evaluatePositive(PositiveCase testCase) {
        List<Document> matches = search(testCase.query());
        int rank = 0;
        for (int index = 0; index < matches.size(); index++) {
            if (testCase.expectedRc().equals(rc(matches.get(index)))) {
                rank = index + 1;
                break;
            }
        }
        Document top = matches.getFirst();
        return new PositiveResult(
                testCase.expectedRc(), testCase.query(), rc(top), score(top), rank);
    }

    private NegativeResult evaluateNegative(String query) {
        Document top = search(query).getFirst();
        return new NegativeResult(query, rc(top), score(top));
    }

    private List<Document> search(String query) {
        List<Document> matches = vectorStore.similaritySearch(SearchRequest.builder()
                .query(query)
                .topK(3)
                .similarityThresholdAll()
                .filterExpression("source == '" + ResponseCodeCatalog.SOURCE + "'")
                .build());
        assertThat(matches).isNotEmpty();
        return matches;
    }

    private ThresholdResult bestThreshold(
            List<PositiveResult> positives, List<NegativeResult> negatives) {
        List<ThresholdResult> candidates = new ArrayList<>();
        for (int value = 50; value <= 95; value++) {
            double threshold = value / 100.0;
            long positiveSuccess = positives.stream()
                    .filter(result -> result.rank() == 1 && result.topScore() >= threshold)
                    .count();
            long negativeSuccess = negatives.stream()
                    .filter(result -> result.topScore() < threshold)
                    .count();
            double balancedAccuracy = (ratio(positiveSuccess, positives.size())
                    + ratio(negativeSuccess, negatives.size())) / 2.0;
            candidates.add(new ThresholdResult(threshold, balancedAccuracy));
        }
        return candidates.stream()
                .max(Comparator.comparingDouble(ThresholdResult::balancedAccuracy)
                        .thenComparingDouble(ThresholdResult::threshold))
                .orElseThrow();
    }

    private String rc(Document document) {
        return document.getMetadata().get("rc").toString();
    }

    private double score(Document document) {
        assertThat(document.getScore()).isNotNull();
        return document.getScore();
    }

    private double ratio(long numerator, int denominator) {
        return (double) numerator / denominator;
    }

    private static PositiveCase positive(String rc, String query) {
        return new PositiveCase(rc, query);
    }

    private record PositiveCase(String expectedRc, String query) {
    }

    private record PositiveResult(
            String expectedRc, String query, String topRc, double topScore, int rank) {
    }

    private record NegativeResult(String query, String topRc, double topScore) {
    }

    private record ThresholdResult(double threshold, double balancedAccuracy) {
    }
}
