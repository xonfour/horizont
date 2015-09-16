package experimental.module.eventfilterproxy.control;

import helper.CommandResultHelper;
import helper.TextFormatHelper;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import module.iface.AbstractProsumerProvider;
import module.iface.DataElementEventListener;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import db.iface.ComponentConfigurationController;
import experimental.module.eventfilterproxy.model.EventFilterOutputStream;
import experimental.module.eventfilterproxy.model.FilterElement;
import framework.constants.ModuleRight;
import framework.control.LogConnector;
import framework.control.ProsumerConnector;
import framework.control.ProviderConnector;
import framework.exception.AuthorizationException;
import framework.exception.BrokerException;
import framework.exception.ModuleException;
import framework.model.DataElement;
import framework.model.Port;
import framework.model.ProsumerPort;
import framework.model.ProviderPort;
import framework.model.event.DataElementEvent;
import framework.model.event.ProviderStateEvent;
import framework.model.type.DataElementType;
import framework.model.type.ModuleStateType;

public class EventFilterProxyModule extends AbstractProsumerProvider implements DataElementEventListener {

	private static final String PORT_ID___PROSUMER = "prosumer";
	private static final String PORT_ID___PROVIDER = "provider";
	public static final int NUM_OF_VALIDATION_THREADS = 10;

	private ExecutorService service = null;
	private final DelayQueue<FilterElement> queuedElements = new DelayQueue<FilterElement>();
	private ProsumerPort prosumerPort;
	private ProviderPort providerPort;
	private final List<String> openOutputStreams = Collections.synchronizedList(new ArrayList<String>());

	private boolean started = false;
	private boolean providerReady = false;

	public EventFilterProxyModule(final ProsumerConnector prosumerConnector, final ProviderConnector providerConnector, final ComponentConfigurationController componentConfiguration, final LogConnector logConnector) {
		super(prosumerConnector, providerConnector, componentConfiguration, logConnector);
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Provider#checkAndLock(framework.model.ProviderPort, java.lang.String[]) */
	@Override
	public int checkAndLock(final ProviderPort port, final String[] path) throws ModuleException {
		try {
			return this.prosumerConnector.checkAndLock(this.prosumerPort, path);
		} catch (BrokerException | AuthorizationException e) {
			throw new ModuleException(e);
		}
	}

	private void checkElementStatus(final FilterElement fe) {
		if (!checkState()) {
			return;
		}
		try {
			final DataElement recentElement = this.prosumerConnector.getElement(this.prosumerPort, fe.getElement().getPath());
			if ((recentElement != null) && recentElement.equals(fe.getElement())) {
				this.queuedElements.add(new FilterElement(recentElement, fe.getEventType(), 5000));
				return;
			}
		} catch (BrokerException | ModuleException | AuthorizationException e) {
			this.logConnector.log(e);
			// in case of an error just forward event with latest element
		}
		try {
			this.providerConnector.sendElementEvent(this.providerPort, fe.getElement(), fe.getEventType());
		} catch (final BrokerException e) {
			this.logConnector.log(e);
		}
	}

	private boolean checkState() {
		return this.started && this.providerReady;
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Provider#createFolder(framework.model.ProviderPort, java.lang.String[]) */
	@Override
	public int createFolder(final ProviderPort port, final String[] path) throws ModuleException {
		try {
			return this.prosumerConnector.createFolder(this.prosumerPort, path);
		} catch (BrokerException | AuthorizationException e) {
			throw new ModuleException(e);
		}
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Provider#delete(framework.model.ProviderPort, java.lang.String[]) */
	@Override
	public int delete(final ProviderPort port, final String[] path) throws ModuleException {
		try {
			return this.prosumerConnector.delete(this.prosumerPort, path);
		} catch (BrokerException | AuthorizationException e) {
			throw new ModuleException(e);
		}
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#enterShutdown() */
	@Override
	public void enterShutdown() {
		this.started = false;
		this.queuedElements.clear();
		if ((this.service != null) && !this.service.isShutdown()) {
			this.service.shutdownNow();
			this.service = null;
		}
		this.openOutputStreams.clear();
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#enterStartup() */
	@Override
	public void enterStartup() {
		this.service = Executors.newFixedThreadPool(EventFilterProxyModule.NUM_OF_VALIDATION_THREADS, new ThreadFactoryBuilder().setNameFormat(EventFilterProxyModule.class.getSimpleName() + "-%d").build());
		for (int i = 0; i < 10; i++) {
			this.service.execute(new Runnable() {

				@Override
				public void run() {
					while (!Thread.currentThread().isInterrupted()) {
						try {
							final FilterElement fe = EventFilterProxyModule.this.queuedElements.take();
							checkElementStatus(fe);
						} catch (final InterruptedException e) {
							return;
						}
					}
				}
			});
		}
		this.started = true;
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#exitShutdown() */
	@Override
	public void exitShutdown() {
		// no op
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#exitStartup() */
	@Override
	public void exitStartup() {
		// no op
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Provider#getChildElements(framework.model.ProviderPort, java.lang.String[], boolean) */
	@Override
	public Set<DataElement> getChildElements(final ProviderPort port, final String[] path, final boolean recursive) throws ModuleException {
		try {
			return this.prosumerConnector.getChildElements(this.prosumerPort, path, recursive);
		} catch (BrokerException | AuthorizationException e) {
			throw new ModuleException(e);
		}
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Provider#getElement(framework.model.ProviderPort, java.lang.String[]) */
	@Override
	public DataElement getElement(final ProviderPort port, final String[] path) throws ModuleException {
		try {
			return this.prosumerConnector.getElement(this.prosumerPort, path);
		} catch (BrokerException | AuthorizationException e) {
			throw new ModuleException(e);
		}
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#getSupportedControlInterfaceCommands() */
	@Override
	public Set<String> getSupportedControlInterfaceCommands() {
		return Collections.emptySet();
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#getSupportedModuleCommands(framework.model.Port, java.lang.String[]) */
	@Override
	public Set<String> getSupportedModuleCommands(final Port port, final String[] path) {
		try {
			if (port == this.prosumerPort) {
				return this.providerConnector.getSupportedModuleCommands(this.providerPort, path);
			} else {
				return this.providerConnector.getSupportedModuleCommands(this.prosumerPort, path);
			}
		} catch (BrokerException | ModuleException | AuthorizationException e) {
			this.logConnector.log(e);
			return null;
		}
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Provider#getType(framework.model.ProviderPort, java.lang.String[]) */
	@Override
	public DataElementType getType(final ProviderPort port, final String[] path) throws ModuleException {
		try {
			return this.prosumerConnector.getType(this.prosumerPort, path);
		} catch (BrokerException | AuthorizationException e) {
			throw new ModuleException(e);
		}
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#initialize() */
	@Override
	public void initialize() {
		try {
			this.prosumerPort = this.prosumerConnector.registerProsumerPort(this, EventFilterProxyModule.PORT_ID___PROSUMER, 1);
			this.providerPort = this.providerConnector.registerProviderPort(this, EventFilterProxyModule.PORT_ID___PROVIDER, -1);
			if ((this.prosumerConnector.getOwnRights() & ModuleRight.RECEIVE_EVENTS) > 0) {
				final String[] rootPath = {};
				this.prosumerConnector.subscribe(this.prosumerPort, rootPath, true, this);
			}
		} catch (BrokerException | AuthorizationException e) {
			this.logConnector.log(e);
		}
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#isReady() */
	@Override
	public boolean isReady() {
		return true;
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Provider#move(framework.model.ProviderPort, java.lang.String[], java.lang.String[]) */
	@Override
	public int move(final ProviderPort port, final String[] srcPath, final String[] destPath) throws ModuleException {
		try {
			return this.prosumerConnector.move(this.prosumerPort, srcPath, destPath);
		} catch (BrokerException | AuthorizationException e) {
			throw new ModuleException(e);
		}
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#onControlInterfaceCommand(java.lang.String, java.util.Map) */
	@Override
	public Map<String, String> onControlInterfaceCommand(final String command, final Map<String, String> properties) {
		return CommandResultHelper.getDefaultResultFail();
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.DataElementEventListener#onElementEvent(framework.model.ProsumerPort, framework.model.event.DataElementEvent) */
	@Override
	public void onElementEvent(final ProsumerPort port, final DataElementEvent event) {
		if (this.openOutputStreams.contains(TextFormatHelper.getPathString(event.dataElement.getPath()))) {
			return; // ignore events for elements that currently get written by this system
		}
		final FilterElement fe = new FilterElement(event.dataElement, event.eventType, 5000);
		this.queuedElements.remove(fe);
		this.queuedElements.put(fe);
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#onModuleCommand(framework.model.Port, java.lang.String, java.lang.String[], java.util.Map) */
	@Override
	public Map<String, String> onModuleCommand(final Port port, final String command, final String[] path, final Map<String, String> properties) {
		try {
			if (port == this.prosumerPort) {
				return this.providerConnector.sendModuleCommand(this.providerPort, command, path, properties);
			} else {
				return this.providerConnector.sendModuleCommand(this.prosumerPort, command, path, properties);
			}
		} catch (BrokerException | ModuleException | AuthorizationException e) {
			this.logConnector.log(e);
			return null;
		}
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#onPortConnection(framework.model.Port) */
	@Override
	public void onPortConnection(final Port port) {
		// no op
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#onPortDisconnection(framework.model.Port) */
	@Override
	public void onPortDisconnection(final Port port) {
		this.queuedElements.clear();
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Prosumer#onProviderStateEvent(framework.model.Port, framework.model.event.ProviderStateEvent) */
	@Override
	public void onProviderStateEvent(final Port port, final ProviderStateEvent event) {
		if ((event.state & ModuleStateType.READY) > 0) {
			this.providerReady = true;
		} else {
			this.providerReady = false;
			this.queuedElements.clear();
		}
		try {
			this.providerConnector.sendState(this.providerPort, event.state);
		} catch (final BrokerException e) {
			this.logConnector.log(e);
		}
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Provider#onStateRequest(framework.model.ProviderPort) */
	@Override
	public void onStateRequest(final ProviderPort port) {
		try {
			this.prosumerConnector.requestConnectedProviderStatus(this.prosumerPort);
		} catch (BrokerException | ModuleException e) {
			this.logConnector.log(e);
		}
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Provider#readData(framework.model.ProviderPort, java.lang.String[]) */
	@Override
	public InputStream readData(final ProviderPort port, final String[] path) throws ModuleException {
		try {
			return this.prosumerConnector.readData(this.prosumerPort, path);
		} catch (BrokerException | AuthorizationException e) {
			throw new ModuleException(e);
		}
	}

	public void removeOutputStream(final String intPath) {
		this.openOutputStreams.remove(intPath);
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Provider#unlock(framework.model.ProviderPort, java.lang.String[]) */
	@Override
	public int unlock(final ProviderPort port, final String[] path) throws ModuleException {
		try {
			return this.prosumerConnector.unlock(this.prosumerPort, path);
		} catch (BrokerException | AuthorizationException e) {
			throw new ModuleException(e);
		}
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Provider#writeData(framework.model.ProviderPort, java.lang.String[]) */
	@Override
	public OutputStream writeData(final ProviderPort port, final String[] path) throws ModuleException {
		final String intPath = TextFormatHelper.getPathString(path);
		try {
			this.openOutputStreams.add(intPath);
			return new EventFilterOutputStream(this, this.prosumerConnector.writeData(this.prosumerPort, path), intPath);
		} catch (BrokerException | AuthorizationException e) {
			this.openOutputStreams.remove(intPath);
			throw new ModuleException(e);
		}
	}
}
