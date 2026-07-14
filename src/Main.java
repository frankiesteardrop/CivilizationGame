import view.MainFrame;
import javax.swing.SwingUtilities;

 /**
 * نقطه ورود اصلی برنامه.
 * مستقر در روت پروژه (بدون پکیج) جهت رعایت استانداردهای Maven و استقلال لایه‌ها.
 */
public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            MainFrame mainFrame = new MainFrame();
            mainFrame.setVisible(true);
        });
    }
}