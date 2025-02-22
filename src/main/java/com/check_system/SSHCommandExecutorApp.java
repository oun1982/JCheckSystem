package com.check_system;

import com.jcraft.jsch.*;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.TextAlignment;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class SSHCommandExecutorApp {
    private JFrame frame;
    private JTextField hostField, userField, passwordField, commandField;
    private JTextArea outputArea;

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(new FlatLightLaf()); // Apply modern FlatLaf theme
        } catch (Exception e) {
            System.err.println("Failed to initialize LaF");
        }
        SwingUtilities.invokeLater(SSHCommandExecutorApp::new);
    }

    public SSHCommandExecutorApp() {
        frame = new JFrame("SSH Command Executor");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 700);
        frame.setLayout(new BorderLayout());

        // Center the frame on the screen
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int x = (int) ((screenSize.getWidth() - frame.getWidth()) / 2);
        int y = (int) ((screenSize.getHeight() - frame.getHeight()) / 2);
        frame.setLocation(x, y);

        JPanel topPanel = new JPanel(new GridLayout(4, 2, 5, 5));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        topPanel.add(new JLabel("SSH Host:"));
        hostField = new JTextField("192.168.60.1");
        topPanel.add(hostField);

        topPanel.add(new JLabel("Username:"));
        userField = new JTextField("root");
        topPanel.add(userField);

        topPanel.add(new JLabel("Password:"));
        passwordField = new JPasswordField();
        topPanel.add(passwordField);
        
        File file = new File("commands.txt");
        System.out.println(file.getAbsolutePath());
        
        topPanel.add(new JLabel("Commands (from file):"));
        commandField = new JTextField(loadCommandsFromFile(file.getAbsolutePath())); // Load commands
        topPanel.add(commandField);

        frame.add(topPanel, BorderLayout.NORTH);

        outputArea = new JTextArea();
        outputArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        outputArea.setEditable(false);
        frame.add(new JScrollPane(outputArea), BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel();
        JButton executeButton = new JButton("Run Commands");
        executeButton.addActionListener(this::executeSSHCommands);
        buttonPanel.add(executeButton);

        JButton exportButton = new JButton("Export to PDF");
        exportButton.addActionListener(this::exportToPDF);
        buttonPanel.add(exportButton);

        frame.add(buttonPanel, BorderLayout.SOUTH);
        frame.setVisible(true);
    }

    private String loadCommandsFromFile(String filePath) {
        StringBuilder commands = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) { // Skip empty lines
                    if (commands.length() > 0) {
                        commands.append(","); // Separate commands with commas
                    }
                    commands.append(line.trim());
                }
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame, "Error reading commands file: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
        return commands.toString();
    }

    private void executeSSHCommands(ActionEvent e) {
        outputArea.setText("Executing SSH commands...\n");

        String host = hostField.getText();
        String user = userField.getText();
        String password = passwordField.getText();
        String[] commands = commandField.getText().split(",");

        List<String> results = SSHExecutor.executeCommands(host, user, password, List.of(commands));

        for (String result : results) {
            outputArea.append(result + "\n-----------------------------------\n");
        }
    }

    private void exportToPDF(ActionEvent e) {
        try {
            List<String> commandResults = List.of(outputArea.getText().split("\n-----------------------------------\n"));

            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setSelectedFile(new File("ssh_output.pdf"));
            int result = fileChooser.showSaveDialog(frame);

            if (result == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();

                PdfWriter writer = new PdfWriter(new FileOutputStream(file));
                PdfDocument pdfDoc = new PdfDocument(writer);
                Document document = new Document(pdfDoc);

                document.add(new Paragraph("SSH Command Output")
                        .setFontSize(18)
                        .setBold()
                        .setTextAlignment(TextAlignment.CENTER)
                        .setMarginBottom(10));

                for (String commandOutput : commandResults) {
                    String[] parts = commandOutput.split("\n", 2);
                    String command = parts[0].replace("Command:", "").trim();
                    String output = (parts.length > 1) ? parts[1] : "No output";

                    document.add(new Paragraph("Command: " + command)
                            .setBold()
                            .setFontSize(12)
                            .setFontColor(ColorConstants.BLUE)
                            .setMarginTop(10));

                    document.add(new Paragraph(output)
                            .setFontSize(11)
                            .setFont(PdfFontFactory.createFont("Courier"))
                            .setFontColor(ColorConstants.DARK_GRAY)
                            .setBackgroundColor(ColorConstants.LIGHT_GRAY)
                            .setPadding(5)
                            .setMarginBottom(10));
                }

                document.close();
                JOptionPane.showMessageDialog(frame, "PDF saved successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, "Error saving PDF: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}

class SSHExecutor {
    public static List<String> executeCommands(String host, String user, String password, List<String> commands) {
        List<String> outputList = new ArrayList<>();

        try {
            JSch jsch = new JSch();
            Session session = jsch.getSession(user, host, 22);
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();

            for (String command : commands) {
                ChannelExec channelExec = (ChannelExec) session.openChannel("exec");
                channelExec.setCommand(command);
                channelExec.setInputStream(null);
                channelExec.setErrStream(System.err);

                InputStream input = channelExec.getInputStream();
                channelExec.connect();

                BufferedReader reader = new BufferedReader(new InputStreamReader(input));
                StringBuilder commandOutput = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    commandOutput.append(line).append("\n");
                }

                outputList.add("Command: " + command + "\n" + commandOutput.toString());
                channelExec.disconnect();
            }

            session.disconnect();
        } catch (Exception e) {
            outputList.add("Error executing commands: " + e.getMessage());
        }

        return outputList;
    }
}
