package cuchaz.enigma.gui.elements;

import cuchaz.enigma.config.Config;
import cuchaz.enigma.config.Themes;
import cuchaz.enigma.gui.Gui;
import cuchaz.enigma.gui.dialog.AboutDialog;
import cuchaz.enigma.gui.dialog.SearchDialog;
import cuchaz.enigma.translation.mapping.serde.MappingsFormat;
import cuchaz.enigma.translation.mapping.serde.MappingsReader;
import cuchaz.enigma.translation.mapping.serde.MappingsWriter;
import cuchaz.enigma.translation.mapping.serde.PathType;

import java.awt.Desktop;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.swing.JFileChooser;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;

public class MenuBar extends JMenuBar {

	public final JMenuItem closeJarMenu;
	public final Set<JMenuItem> saveLoadMenuItems = new HashSet<>();
	public final JMenuItem saveMappingsMenu;
	public final JMenuItem closeMappingsMenu;
	public final JMenuItem dropMappingsMenu;
	public final JMenuItem exportSourceMenu;
	public final JMenuItem exportJarMenu;
	private final Gui gui;

	public MenuBar(Gui gui) {
		this.gui = gui;

		{
			JMenu menu = new JMenu("File");
			this.add(menu);
			{
				JMenuItem item = new JMenuItem("Open Jar...");
				menu.add(item);
				item.addActionListener(event -> {
					this.gui.jarFileChooser.setVisible(true);
					Path path = Paths.get(this.gui.jarFileChooser.getDirectory()).resolve(this.gui.jarFileChooser.getFile());
					if (Files.exists(path)) {
						gui.getController().openJar(path);
					}
				});
			}
			{
				JMenuItem item = new JMenuItem("Close Jar");
				menu.add(item);
				item.addActionListener(event -> this.gui.getController().closeJar());
				this.closeJarMenu = item;
			}

			{
				menu.addSeparator();
				JMenu openMenu = new JMenu("Open Mappings...");
				menu.add(openMenu);

				for (Map.Entry<String, MappingsFormat> format : gui.getController().enigma.getMappingsFormats()) {
					for (Map.Entry<String, MappingsReader> reader : format.getValue().getReaders().entrySet()) {
						String name = String.format("%s (%s)", format.getKey(), reader.getKey());
						EnumSet<PathType> pathTypes = reader.getValue().getSupportedPathTypes();
						JMenuItem item = new JMenuItem(name);
						openMenu.add(item);
						item.addActionListener(event -> {
							if (pathTypes.contains(PathType.FILE) && pathTypes.contains(PathType.DIRECTORY)) {
								if (this.gui.fileChooserAny.showOpenDialog(this.gui.getFrame()) == JFileChooser.APPROVE_OPTION) {
									File selectedFile = this.gui.fileChooserAny.getSelectedFile();
									this.gui.getController().openMappings(format.getValue(), reader.getValue(), selectedFile.toPath());
								}
							} else if (pathTypes.contains(PathType.FILE)) {
								this.gui.fileChooserOpenFile.setVisible(true);
								Path file = Paths.get(this.gui.fileChooserOpenFile.getDirectory(), this.gui.fileChooserOpenFile.getFile());
								if (Files.exists(file)) {
									this.gui.getController().openMappings(format.getValue(), reader.getValue(), file);
								}
							} else if (pathTypes.contains(PathType.DIRECTORY)) {
								if (this.gui.fileChooserDirectory.showOpenDialog(this.gui.getFrame()) == JFileChooser.APPROVE_OPTION) {
									File selectedFile = this.gui.fileChooserDirectory.getSelectedFile();
									this.gui.getController().openMappings(format.getValue(), reader.getValue(), selectedFile.toPath());
								}
							} else {
								throw new IllegalStateException("Mappings format " + format.getKey() + " with reader " +
										reader.getKey() + " supports neither files or directories");
							}
						});
						this.saveLoadMenuItems.add(item);
					}
				}
			}
			{
				JMenuItem item = new JMenuItem("Save Mappings");
				menu.add(item);
				item.addActionListener(event ->
                        this.gui.getController().saveMappings(this.gui.fileChooserAny.getSelectedFile().toPath()));
				item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK));
				this.saveMappingsMenu = item;
			}

			{
                JMenu saveMenu = new JMenu("Save Mappings As...");
                menu.add(saveMenu);

                for (Map.Entry<String, MappingsFormat> format : gui.getController().enigma.getMappingsFormats()) {
                    for (Map.Entry<String, MappingsWriter> writer : format.getValue().getWriters().entrySet()) {
                        String name = String.format("%s (%s)", format.getKey(), writer.getKey());
                        EnumSet<PathType> pathTypes = writer.getValue().getSupportedPathTypes();
                        JMenuItem item = new JMenuItem(name);
                        saveMenu.add(item);
                        item.addActionListener(event -> {
                            if (pathTypes.contains(PathType.FILE) && pathTypes.contains(PathType.DIRECTORY)) {
                                if (this.gui.fileChooserAny.showSaveDialog(this.gui.getFrame()) == JFileChooser.APPROVE_OPTION) {
                                    this.gui.getController().saveMappings(this.gui.fileChooserAny.getSelectedFile().toPath(),
                                            format.getValue(), writer.getValue());
                                    this.saveMappingsMenu.setEnabled(true);
                                }
                            } else if (pathTypes.contains(PathType.FILE)) {
                                this.gui.fileChooserSaveFile.setVisible(true);
                                Path file = Paths.get(this.gui.fileChooserSaveFile.getDirectory(), this.gui.fileChooserSaveFile.getFile());
                                if (Files.exists(file)) {
                                    this.gui.getController().saveMappings(file, format.getValue(), writer.getValue());
                                    this.saveMappingsMenu.setEnabled(true);
                                }
                            } else if (pathTypes.contains(PathType.DIRECTORY)) {
                                if (this.gui.fileChooserDirectory.showSaveDialog(this.gui.getFrame()) == JFileChooser.APPROVE_OPTION) {
                                    this.gui.getController().saveMappings(this.gui.fileChooserDirectory.getSelectedFile().toPath(),
                                            format.getValue(), writer.getValue());
                                    this.saveMappingsMenu.setEnabled(true);
                                }
                            } else {
                                throw new IllegalStateException("Mappings format " + format.getKey() + " with writer " +
                                        writer.getKey() + " supports neither files or directories");
                            }
                        });
                        this.saveLoadMenuItems.add(item);
                    }
                }
			}
			{
				JMenuItem item = new JMenuItem("Close Mappings");
				menu.add(item);
				item.addActionListener(event -> {
					if (this.gui.getController().isDirty()) {
						this.gui.showDiscardDiag((response -> {
							if (response == JOptionPane.YES_OPTION) {
								gui.saveMapping();
								this.gui.getController().closeMappings();
							} else if (response == JOptionPane.NO_OPTION)
								this.gui.getController().closeMappings();
							return null;
						}), "Save and close", "Discard changes", "Cancel");
					} else
						this.gui.getController().closeMappings();

				});
				this.closeMappingsMenu = item;
			}
			{
				JMenuItem item = new JMenuItem("Drop Invalid Mappings");
				menu.add(item);
				item.addActionListener(event -> this.gui.getController().dropMappings());
				this.dropMappingsMenu = item;
			}
			menu.addSeparator();
			{
				JMenuItem item = new JMenuItem("Export Source...");
				menu.add(item);
				item.addActionListener(event -> {
					if (this.gui.exportSourceFileChooser.showSaveDialog(this.gui.getFrame()) == JFileChooser.APPROVE_OPTION) {
						this.gui.getController().exportSource(this.gui.exportSourceFileChooser.getSelectedFile().toPath());
					}
				});
				this.exportSourceMenu = item;
			}
			{
				JMenuItem item = new JMenuItem("Export Jar...");
				menu.add(item);
				item.addActionListener(event -> {
					this.gui.exportJarFileChooser.setVisible(true);
					if (this.gui.exportJarFileChooser.getFile() != null) {
						Path path = Paths.get(this.gui.exportJarFileChooser.getDirectory(), this.gui.exportJarFileChooser.getFile());
						this.gui.getController().exportJar(path);
					}
				});
				this.exportJarMenu = item;
			}
			menu.addSeparator();
			{
				JMenuItem item = new JMenuItem("Exit");
				menu.add(item);
				item.addActionListener(event -> this.gui.close());
			}
		}
		{
			JMenu menu = new JMenu("View");
			this.add(menu);
			{
				JMenu themes = new JMenu("Themes");
				menu.add(themes);
				for (Config.LookAndFeel lookAndFeel : Config.LookAndFeel.values()) {
					JMenuItem theme = new JMenuItem(lookAndFeel.getName());
					themes.add(theme);
					theme.addActionListener(event -> Themes.setLookAndFeel(gui, lookAndFeel));
				}

				JMenuItem search = new JMenuItem("Search");
				search.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, InputEvent.SHIFT_MASK));
				menu.add(search);
				search.addActionListener(event -> {
					if (this.gui.getController().project != null) {
						new SearchDialog(this.gui).show();
					}
				});

			}
		}
		{
			JMenu menu = new JMenu("Help");
			this.add(menu);
			{
				JMenuItem item = new JMenuItem("About");
				menu.add(item);
				item.addActionListener(event -> AboutDialog.show(this.gui.getFrame()));
			}
			{
				JMenuItem item = new JMenuItem("GitHub Page");
				menu.add(item);
				item.addActionListener(event -> {
					try {
						Desktop.getDesktop().browse(new URL("https://github.com/FabricMC/Enigma").toURI());
					} catch (URISyntaxException | IOException ignored) {
					}
				});
			}
		}
	}
}
