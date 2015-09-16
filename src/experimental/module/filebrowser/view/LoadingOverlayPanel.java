package experimental.module.filebrowser.view;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;

import net.miginfocom.swing.MigLayout;

/**
 *
 * @author Stefan Werner
 */
public class LoadingOverlayPanel extends JPanel {

	private static final long serialVersionUID = 1152496709076416020L;

	private JProgressBar progressBar;
	private JLabel loadingLabel;
	private JButton abortButton;
	private int progress = 0;
	private int maxProgress = 0;

	/**
	 * Create the panel.
	 */
	public LoadingOverlayPanel(final ActionListener cancelAction) {
		initialize();
		this.abortButton.addActionListener(cancelAction);
	}

	public void hideLoadingComponents() {
		this.progressBar.setVisible(false);
		this.abortButton.setVisible(false);
	}

	public void increaseProgress() {
		setProgress(this.progress + 1);
	}

	private void initialize() {
		setLayout(new MigLayout("ins 0 2 0 2", "[][grow]", "[]"));

		this.loadingLabel = new JLabel();
		this.loadingLabel.setMaximumSize(new Dimension(200, 32));
		this.loadingLabel.setFont(new Font("Dialog", Font.PLAIN, 11));
		add(this.loadingLabel, "cell 0 0,alignx right,aligny center");

		this.progressBar = new JProgressBar();
		this.progressBar.setFont(new Font("Dialog", Font.PLAIN, 11));
		this.progressBar.setStringPainted(true);
		this.progressBar.setString("");
		this.progressBar.setIndeterminate(true);
		this.progressBar.setPreferredSize(new Dimension(100, 10));
		add(this.progressBar, "flowx,cell 1 0,alignx right,aligny center");

		this.abortButton = new JButton();
		this.abortButton.setBorderPainted(false);
		this.abortButton.setContentAreaFilled(false);
		this.abortButton.setFocusPainted(false);
		this.abortButton.setOpaque(false);
		final ImageIcon abortIcon = new ImageIcon(LoadingOverlayPanel.class.getResource("/icons/key_unknown_xs.png"));
		this.abortButton.setIcon(abortIcon);
		this.abortButton.setMaximumSize(new Dimension(abortIcon.getIconWidth(), abortIcon.getIconHeight()));
		this.abortButton.addActionListener(new ActionListener() {
			/* (non-Javadoc)
			 *
			 * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent) */
			@Override
			public void actionPerformed(final ActionEvent e) {
				LoadingOverlayPanel.this.abortButton.setEnabled(false);
			}
		});
		add(this.abortButton, "cell 1 0,alignx right,aligny center");
		hideLoadingComponents();
	}

	public void resetProgress() {
		this.progress = 0;
		this.progressBar.setValue(this.progress);
		this.progressBar.setString("");
		this.progressBar.setIndeterminate(true);
		showLoadingComponents();
	}

	public void setMaxProgress(final int maxProgress) {
		this.maxProgress = maxProgress;
		this.progressBar.setMaximum(maxProgress);
	}

	public void setProgress(final int progress) {
		this.progressBar.setIndeterminate(false);
		this.progress = progress;
		this.progressBar.setValue(progress);
		this.progressBar.setString(progress + " / " + this.maxProgress);
		// if (progress >= maxProgress) {
		// hideLoadingComponents();
		// }
	}

	public void setStatusText(final String status) {
		this.loadingLabel.setText(status);
	}

	public void showLoadingComponents() {
		this.progressBar.setVisible(true);
		this.abortButton.setVisible(true);
		this.abortButton.setEnabled(true);
	}
}
