import view.MainFrame;
import javax.swing.SwingUtilities;

/**
 * نقطه ورود اصلی برنامه.
 * مستقر در روت پروژه (بدون پکیج) جهت رعایت استانداردهای Maven و استقلال لایه‌ها.
 */
public class Main {
    public static void main(String[] args) {
        // اجرای رابط کاربری در Thread مخصوص Swing برای جلوگیری از فریز شدن گرافیک
        SwingUtilities.invokeLater(() -> {
            MainFrame mainFrame = new MainFrame();
            mainFrame.setVisible(true);
        });
    }
}