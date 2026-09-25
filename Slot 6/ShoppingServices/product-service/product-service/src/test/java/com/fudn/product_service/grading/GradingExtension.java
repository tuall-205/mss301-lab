package com.fudn.product_service.grading;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * JUnit 5 Extension dùng để chấm điểm tự động.
 *
 * Cách hoạt động:
 *  - Sau mỗi @Test, đọc annotation @Points trên method để biết điểm tối đa của test đó.
 *  - Nếu test PASS  -> cộng điểm tối đa.
 *  - Nếu test FAIL  -> 0 điểm cho test đó.
 *  - Sau khi tất cả test class chạy xong, ghi báo cáo ra:
 *      target/grading-report.txt
 *      target/grading-report.json
 */
public class GradingExtension implements AfterTestExecutionCallback, AfterAllCallback {

    /** Kết quả của 1 test. */
    private static class Result {
        String testName;
        String description;
        double maxPoints;
        double earnedPoints;
        boolean passed;
        String errorMessage;
    }

    // dùng static để gom kết quả của tất cả test methods trong cùng class
    private static final List<Result> RESULTS = new ArrayList<>();

    @Override
    public void afterTestExecution(ExtensionContext context) {
        Points points = context.getRequiredTestMethod().getAnnotation(Points.class);
        // Test không có @Points -> bỏ qua, không tính vào điểm
        if (points == null) {
            return;
        }

        Result r = new Result();
        r.testName = context.getRequiredTestMethod().getName();
        r.description = points.description();
        r.maxPoints = points.value();
        r.passed = context.getExecutionException().isEmpty();
        r.earnedPoints = r.passed ? points.value() : 0.0;
        r.errorMessage = context.getExecutionException()
                .map(Throwable::getMessage)
                .orElse("");
        RESULTS.add(r);
    }

    @Override
    public void afterAll(ExtensionContext context) throws IOException {
        // Tổng kết
        double totalMax = RESULTS.stream().mapToDouble(r -> r.maxPoints).sum();
        double totalEarned = RESULTS.stream().mapToDouble(r -> r.earnedPoints).sum();

        // ===== File báo cáo dạng text dễ đọc =====
        StringBuilder txt = new StringBuilder();
        txt.append("=========================================================\n");
        txt.append(" AUTOGRADING REPORT - Product Service (Update & Delete)\n");
        txt.append("=========================================================\n");
        txt.append(String.format(" Test class : %s%n", context.getRequiredTestClass().getSimpleName()));
        txt.append(String.format(" Tổng số test có điểm : %d%n", RESULTS.size()));
        txt.append("---------------------------------------------------------\n");

        for (Result r : RESULTS) {
            txt.append(String.format(" [%s] %-45s %.2f / %.2f%n",
                    r.passed ? "PASS" : "FAIL",
                    r.description.isEmpty() ? r.testName : r.description,
                    r.earnedPoints,
                    r.maxPoints));
            if (!r.passed && r.errorMessage != null && !r.errorMessage.isEmpty()) {
                txt.append("        Lỗi: ").append(r.errorMessage).append("\n");
            }
        }
        txt.append("---------------------------------------------------------\n");
        txt.append(String.format(" TỔNG ĐIỂM: %.2f / %.2f%n", totalEarned, totalMax));
        txt.append("=========================================================\n");

        // ===== File báo cáo dạng JSON cho hệ thống tự động đọc =====
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"testClass\": \"").append(context.getRequiredTestClass().getName()).append("\",\n");
        json.append("  \"totalEarned\": ").append(totalEarned).append(",\n");
        json.append("  \"totalMax\": ").append(totalMax).append(",\n");
        json.append("  \"results\": [\n");
        for (int i = 0; i < RESULTS.size(); i++) {
            Result r = RESULTS.get(i);
            json.append("    {");
            json.append("\"test\": \"").append(escape(r.testName)).append("\", ");
            json.append("\"description\": \"").append(escape(r.description)).append("\", ");
            json.append("\"passed\": ").append(r.passed).append(", ");
            json.append("\"earned\": ").append(r.earnedPoints).append(", ");
            json.append("\"max\": ").append(r.maxPoints).append(", ");
            json.append("\"error\": \"").append(escape(r.errorMessage == null ? "" : r.errorMessage)).append("\"");
            json.append("}");
            if (i < RESULTS.size() - 1) json.append(",");
            json.append("\n");
        }
        json.append("  ]\n");
        json.append("}\n");

        // Ghi file
        Path targetDir = Paths.get("target");
        Files.createDirectories(targetDir);
        Files.writeString(targetDir.resolve("grading-report.txt"), txt.toString());
        Files.writeString(targetDir.resolve("grading-report.json"), json.toString());

        // In ra console để xem nhanh
        System.out.println(txt);

        // dọn dẹp để các lần chạy sau không bị cộng dồn
        RESULTS.clear();
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }
}
