package app.ui;

import app.support.AppInfo;
import app.support.MenuDisplay;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

public final class CreditsPanel extends JPanel {
    public CreditsPanel(Runnable onBack) {
        Dimension preferredSize = MenuDisplay.preferredWindowSize();
        double scale = MenuDisplay.scaleFor(preferredSize);

        setPreferredSize(preferredSize);
        setBackground(new Color(8, 12, 20));
        setFocusable(true);

        JLabel title = new JLabel("Credits");
        title.setForeground(Color.WHITE);
        title.setFont(MenuDisplay.font("Consolas", Font.BOLD, 52, scale));

        JTextArea body = new JTextArea();
        body.setEditable(false);
        body.setOpaque(false);
        body.setFocusable(false);
        body.setForeground(new Color(218, 218, 218));
        body.setFont(MenuDisplay.font("Consolas", Font.PLAIN, 20, scale));
        body.setMargin(new Insets(MenuDisplay.scaled(10, scale), 0, MenuDisplay.scaled(10, scale), 0));
        StringBuilder sb = new StringBuilder();
        for (String line : AppInfo.creditsLines()) {
            sb.append(line).append('\n');
        }
        body.setText(sb.toString().trim());

        JButton back = new JButton("Back");
        back.setFont(MenuDisplay.font("Consolas", Font.BOLD, 16, scale));
        back.setFocusPainted(false);
        back.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(120, 156, 190)),
                BorderFactory.createEmptyBorder(
                        MenuDisplay.scaled(6, scale),
                        MenuDisplay.scaled(18, scale),
                        MenuDisplay.scaled(6, scale),
                        MenuDisplay.scaled(18, scale))
        ));
        back.addActionListener(e -> onBack.run());

        JPanel card = new JPanel(new GridBagLayout());
        card.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.insets = new Insets(
                MenuDisplay.scaled(8, scale),
                MenuDisplay.scaled(8, scale),
                MenuDisplay.scaled(8, scale),
                MenuDisplay.scaled(8, scale));
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
