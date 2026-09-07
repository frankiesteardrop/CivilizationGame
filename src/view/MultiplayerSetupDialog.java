package view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class MultiplayerSetupDialog extends JDialog {

    private static final Color BG_DARK      = new Color(18, 22, 30);
    private static final Color BG_CARD      = new Color(28, 33, 44);
    private static final Color ACCENT_GOLD  = new Color(220, 185, 45);
    private static final Color ACCENT_BLUE  = new Color(52, 130, 215);
    private static final Color ACCENT_GREEN = new Color(46, 180, 80);
    private static final Color TEXT_MAIN    = new Color(225, 230, 240);
    private static final Color TEXT_DIM     = new Color(130, 140, 158);

    private JTextField usernameField;
    private JPasswordField passwordField;
    private JTextField ipField;

    private final JTabbedPane tabs;

    private String  username = null;
    private String  password = null;
    private String  serverIp = null;
    private boolean hostMode = false;

    public MultiplayerSetupDialog(Frame parent) {
        super(parent, "Multiplayer Setup", true);
        setUndecorated(true);
        setSize(440, 420);
        setLocationRelativeTo(parent);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG_DARK);
        root.setBorder(BorderFactory.createLineBorder(ACCENT_GOLD, 2));

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(22, 27, 38));
        header.setBorder(new EmptyBorder(16, 20, 16, 20));
        JLabel title = new JLabel("🌐  MULTIPLAYER", SwingConstants.CENTER);
        title.setFont(new Font("Georgia", Font.BOLD, 22));
        title.setForeground(ACCENT_GOLD);
        header.add(title, BorderLayout.CENTER);
        root.add(header, BorderLayout.NORTH);

        JPanel authPanel = new JPanel(new GridLayout(2, 1, 0, 10));
        authPanel.setBackground(BG_DARK);
        authPanel.setBorder(new EmptyBorder(12, 20, 4, 20));

        JPanel usernamePanel = new JPanel(new BorderLayout(8, 0));
        usernamePanel.setOpaque(false);
        JLabel usernameLabel = new JLabel("Username:");
        usernameLabel.setForeground(TEXT_DIM);
        usernameLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));

        usernameField = new JTextField("Player1");
        styleTextField(usernameField);
        usernamePanel.add(usernameLabel, BorderLayout.WEST);
        usernamePanel.add(usernameField, BorderLayout.CENTER);

        JPanel passwordPanel = new JPanel(new BorderLayout(12, 0));
        passwordPanel.setOpaque(false);
        JLabel passwordLabel = new JLabel("Password:");
        passwordLabel.setForeground(TEXT_DIM);
        passwordLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));

        passwordField = new JPasswordField();
        styleTextField(passwordField);
        passwordPanel.add(passwordLabel, BorderLayout.WEST);
        passwordPanel.add(passwordField, BorderLayout.CENTER);

        authPanel.add(usernamePanel);
        authPanel.add(passwordPanel);

        tabs = new JTabbedPane();
        tabs.setBackground(BG_DARK);
        tabs.setForeground(TEXT_MAIN);
        tabs.setFont(new Font("Segoe UI", Font.BOLD, 13));
        tabs.addTab("🖥️  Host Game", buildHostPanel());
        tabs.addTab("🌐  Join Game", buildJoinPanel());

        JPanel center = new JPanel(new BorderLayout(0, 4));
        center.setBackground(BG_DARK);
        center.add(authPanel, BorderLayout.NORTH);
        center.add(tabs,      BorderLayout.CENTER);
        root.add(center, BorderLayout.CENTER);

        JButton cancelBtn = buildButton("✕  Cancel", new Color(80, 40, 40), Color.WHITE);
        cancelBtn.addActionListener(e -> dispose());
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 10));
        footer.setBackground(new Color(18, 22, 30));
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(50, 58, 72)));
        footer.add(cancelBtn);
        root.add(footer, BorderLayout.SOUTH);

        setContentPane(root);
    }

    private void styleTextField(JTextField field) {
        field.setBackground(BG_CARD);
        field.setForeground(TEXT_MAIN);
        field.setCaretColor(TEXT_MAIN);
        field.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(60, 70, 90), 1),
                new EmptyBorder(6, 10, 6, 10)));
    }

    private JPanel buildHostPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_DARK);
        panel.setBorder(new EmptyBorder(20, 20, 16, 20));

        JLabel info = new JLabel(
                "<html><center>Start a server on <b>localhost:8080</b>.<br/>"
                        + "Other players can join using your IP address.</center></html>",
                SwingConstants.CENTER);
        info.setForeground(TEXT_DIM);
        info.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        JButton hostBtn = buildButton("🖥️  Start Server & Host", ACCENT_GREEN, Color.WHITE);
        hostBtn.addActionListener(e -> {
            if (validateAuth()) {
                username = usernameField.getText().trim();
                password = new String(passwordField.getPassword());
                serverIp = "localhost";
                hostMode = true;
                dispose();
            }
        });

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        btnPanel.setOpaque(false);
        btnPanel.add(hostBtn);

        panel.add(info,     BorderLayout.CENTER);
        panel.add(btnPanel, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildJoinPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(BG_DARK);
        panel.setBorder(new EmptyBorder(20, 20, 16, 20));

        JPanel ipPanel = new JPanel(new BorderLayout(8, 0));
        ipPanel.setOpaque(false);

        JLabel ipLabel = new JLabel("Server IP:");
        ipLabel.setForeground(TEXT_DIM);
        ipLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));

        ipField = new JTextField("192.168.1.1");
        styleTextField(ipField);

        ipPanel.add(ipLabel, BorderLayout.WEST);
        ipPanel.add(ipField, BorderLayout.CENTER);

        JButton joinBtn = buildButton("🌐  Connect & Join", ACCENT_BLUE, Color.WHITE);
        joinBtn.addActionListener(e -> {
            if (validateAuth() && validateIp()) {
                username = usernameField.getText().trim();
                password = new String(passwordField.getPassword());
                serverIp = ipField.getText().trim();
                hostMode = false;
                dispose();
            }
        });

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        btnPanel.setOpaque(false);
        btnPanel.add(joinBtn);

        panel.add(ipPanel,  BorderLayout.NORTH);
        panel.add(btnPanel, BorderLayout.SOUTH);
        return panel;
    }

    private boolean validateAuth() {
        String name = usernameField.getText().trim();
        String pwd = new String(passwordField.getPassword());
        if (name.isEmpty() || name.length() > 20) {
            JOptionPane.showMessageDialog(this, "Please enter a username (1–20 chars).", "Invalid", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        if (pwd.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Password is required for server authentication.", "Invalid", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        return true;
    }

    private boolean validateIp() {
        if (ipField.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter the server IP.", "Invalid", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        return true;
    }

    private JButton buildButton(String text, Color bg, Color fg) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFocusPainted(false);
        btn.setOpaque(true);
        btn.setBorderPainted(false);
        btn.setBorder(new EmptyBorder(9, 20, 9, 20));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { btn.setBackground(bg.brighter()); }
            @Override public void mouseExited(MouseEvent e)  { btn.setBackground(bg); }
        });
        return btn;
    }

    public String  getUsername() { return username; }
    public String  getPassword() { return password; }
    public String  getServerIp() { return serverIp; }
    public boolean isHostMode()  { return hostMode; }
}