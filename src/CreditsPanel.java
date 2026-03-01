import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

final class CreditsPanel extends JPanel {
    CreditsPanel(Runnable onBack) {
        setPreferredSize(new Dimension(1280, 720));
        setBackground(new Color(8, 12, 20));
        setFocusable(true);

        JLabel title = new JLabel("Credits");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("Consolas", Font.BOLD, 52));

        JTextArea body = new JTextArea();
        body.setEditable(false);
        body.setOpaque(false);
        body.setFocusable(false);
        body.setForeground(new Color(218, 218, 218));
        body.setFont(new Font("Consolas", Font.PLAIN, 20));
        body.setMargin(new Insets(10, 0, 10, 0));
        StringBuilder sb = new StringBuilder();
        for (String line : AppInfo.creditsLines()) {
            sb.append(line).append('\n');
        }
        body.setText(sb.toString().trim());

        JButton back = new JButton("Back");
        back.addActionListener(e -> onBack.run());

        JPanel card = new JPanel(new GridBagLayout());
        card.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.insets = new Insets(8, 8, 8, 8);
        c.anchor = GridBagConstraints.CENTER;
        card.add(title, c);

        c.gridy++;
        card.add(body, c);

        c.gridy++;
        card.add(back, c);

        setLayout(new GridBagLayout());
        add(card);

        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "back");
        getActionMap().put("back", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onBack.run();
            }
        });
    }
}
