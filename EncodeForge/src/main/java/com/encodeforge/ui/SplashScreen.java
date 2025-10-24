package com.encodeforge.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * Pure Swing-based splash screen that shows during application initialization.
 * 
 * This uses AWT/Swing instead of JavaFX to ensure it can display even if JavaFX
 * initialization fails. It provides visual feedback during startup and can show
 * errors that occur before the main UI is ready.
 * 
 * Features:
 * - Shows immediately on startup
 * - Updates progress during initialization
 * - Displays errors if initialization fails
 * - Works independently of JavaFX
 */
public class SplashScreen extends JWindow {
    private static final long serialVersionUID = 1L;
    
    private static final int SPLASH_WIDTH = 500;
    private static final int SPLASH_HEIGHT = 300;
    private static final int CORNER_RADIUS = 20;
    
    // UI Components
    private final JProgressBar progressBar;
    private final JLabel statusLabel;
    private final JLabel versionLabel;
    private final JLabel titleLabel;
    
    // Singleton instance
    private static SplashScreen instance;
    
    /**
     * Private constructor - use getInstance()
     */
    private SplashScreen() {
        // Set up the main panel with rounded corners
        JPanel contentPanel = new JPanel() {
            private static final long serialVersionUID = 1L;
            
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                // Dark gradient background
                GradientPaint gradient = new GradientPaint(
                    0, 0, new Color(28, 28, 30),
                    0, getHeight(), new Color(10, 10, 10)
                );
                g2.setPaint(gradient);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), CORNER_RADIUS, CORNER_RADIUS);
                
                // Subtle border
                g2.setColor(new Color(58, 58, 60));
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, CORNER_RADIUS, CORNER_RADIUS);
                
                g2.dispose();
            }
        };
        contentPanel.setLayout(new BorderLayout(0, 20));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(40, 40, 40, 40));
        contentPanel.setOpaque(false);
        
        // Title Section
        JPanel titlePanel = new JPanel();
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));
        titlePanel.setOpaque(false);
        
        // App Icon/Title
        titleLabel = new JLabel("EncodeForge");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 32));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        // Version
        versionLabel = new JLabel("Version 0.4.1");
        versionLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        versionLabel.setForeground(new Color(160, 160, 160));
        versionLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        titlePanel.add(titleLabel);
        titlePanel.add(Box.createVerticalStrut(8));
        titlePanel.add(versionLabel);
        
        // Progress Section
        JPanel progressPanel = new JPanel();
        progressPanel.setLayout(new BoxLayout(progressPanel, BoxLayout.Y_AXIS));
        progressPanel.setOpaque(false);
        
        // Status Label
        statusLabel = new JLabel("Initializing...");
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        statusLabel.setForeground(new Color(180, 180, 180));
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        // Progress Bar
        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setPreferredSize(new Dimension(400, 8));
        progressBar.setMaximumSize(new Dimension(400, 8));
        progressBar.setBorderPainted(false);
        progressBar.setOpaque(false);
        
        // Style the progress bar
        progressBar.setUI(new javax.swing.plaf.basic.BasicProgressBarUI() {
            @Override
            protected Color getSelectionBackground() {
                return Color.WHITE;
            }
            
            @Override
            protected Color getSelectionForeground() {
                return Color.WHITE;
            }
            
            @Override
            protected void paintDeterminate(Graphics g, JComponent c) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                // Background track
                g2.setColor(new Color(60, 60, 60));
                g2.fillRoundRect(0, 0, c.getWidth(), c.getHeight(), 4, 4);
                
                // Progress bar
                int width = (int) (c.getWidth() * progressBar.getPercentComplete());
                GradientPaint gradient = new GradientPaint(
                    0, 0, new Color(10, 132, 255),
                    width, 0, new Color(64, 224, 208)
                );
                g2.setPaint(gradient);
                g2.fillRoundRect(0, 0, width, c.getHeight(), 4, 4);
                
                g2.dispose();
            }
            
            @Override
            protected void paintIndeterminate(Graphics g, JComponent c) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                // Background track
                g2.setColor(new Color(60, 60, 60));
                g2.fillRoundRect(0, 0, c.getWidth(), c.getHeight(), 4, 4);
                
                // Animated indeterminate bar
                int boxWidth = 80;
                int boxX = getAnimationIndex() % (c.getWidth() + boxWidth) - boxWidth;
                
                if (boxX + boxWidth > 0 && boxX < c.getWidth()) {
                    GradientPaint gradient = new GradientPaint(
                        boxX, 0, new Color(10, 132, 255, 200),
                        boxX + boxWidth, 0, new Color(64, 224, 208, 200)
                    );
                    g2.setPaint(gradient);
                    
                    int startX = Math.max(0, boxX);
                    int endX = Math.min(c.getWidth(), boxX + boxWidth);
                    g2.fillRoundRect(startX, 0, endX - startX, c.getHeight(), 4, 4);
                }
                
                g2.dispose();
            }
        });
        
        progressPanel.add(statusLabel);
        progressPanel.add(Box.createVerticalStrut(15));
        progressPanel.add(progressBar);
        
        // Add components to main panel
        contentPanel.add(titlePanel, BorderLayout.NORTH);
        contentPanel.add(progressPanel, BorderLayout.CENTER);
        
        // Footer
        JLabel copyrightLabel = new JLabel("© 2025 EncodeForge");
        copyrightLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        copyrightLabel.setForeground(new Color(120, 120, 120));
        copyrightLabel.setHorizontalAlignment(SwingConstants.CENTER);
        contentPanel.add(copyrightLabel, BorderLayout.SOUTH);
        
        // Set up the window
        setContentPane(contentPanel);
        setSize(SPLASH_WIDTH, SPLASH_HEIGHT);
        setLocationRelativeTo(null); // Center on screen
        
        // Make it rounded (platform dependent)
        try {
            setShape(new RoundRectangle2D.Double(0, 0, SPLASH_WIDTH, SPLASH_HEIGHT, 
                CORNER_RADIUS, CORNER_RADIUS));
        } catch (Exception e) {
            // Some platforms don't support setShape
            System.err.println("Could not set rounded shape: " + e.getMessage());
        }
    }
    
    /**
     * Get the singleton splash screen instance
     */
    public static synchronized SplashScreen getInstance() {
        if (instance == null) {
            instance = new SplashScreen();
        }
        return instance;
    }
    
    /**
     * Show the splash screen
     */
    public static void showSplash() {
        SwingUtilities.invokeLater(() -> {
            SplashScreen splash = getInstance();
            splash.setVisible(true);
            splash.toFront();
        });
    }
    
    /**
     * Close the splash screen
     */
    public static void closeSplash() {
        SwingUtilities.invokeLater(() -> {
            if (instance != null) {
                instance.setVisible(false);
                instance.dispose();
                instance = null;
            }
        });
    }
    
    /**
     * Update the status message
     */
    public static void updateStatus(String status) {
        SwingUtilities.invokeLater(() -> {
            if (instance != null) {
                instance.statusLabel.setText(status);
            }
        });
    }
    
    /**
     * Set progress (0-100). Use -1 for indeterminate.
     */
    public static void setProgress(int progress) {
        SwingUtilities.invokeLater(() -> {
            if (instance != null) {
                if (progress < 0) {
                    instance.progressBar.setIndeterminate(true);
                } else {
                    instance.progressBar.setIndeterminate(false);
                    instance.progressBar.setValue(progress);
                }
            }
        });
    }
    
    /**
     * Show an error message in the splash screen
     */
    public static void showError(String title, String message) {
        SwingUtilities.invokeLater(() -> {
            closeSplash();
            JOptionPane.showMessageDialog(
                null,
                message,
                title,
                JOptionPane.ERROR_MESSAGE
            );
        });
    }
}
