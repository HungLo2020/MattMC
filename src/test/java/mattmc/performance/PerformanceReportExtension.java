package mattmc.performance;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * JUnit 5 extension that generates the performance report after all tests complete.
 * 
 * Apply to a test class with:
 * <pre>
 * @ExtendWith(PerformanceReportExtension.class)
 * </pre>
 * 
 * Or register globally in junit-platform.properties.
 */
public class PerformanceReportExtension implements AfterAllCallback {
    
    private static boolean reportGenerated = false;
    
    @Override
    public void afterAll(ExtensionContext context) {
        // Generate report only once, after the last test class
        if (!reportGenerated) {
            reportGenerated = true;
            
            // Use a shutdown hook to ensure report is generated after all tests
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                PerformanceReportGenerator.generateReport();
                System.out.println("\n📊 Performance report generated at: build/reports/performance/");
            }));
        }
    }
}
