package io.left.rightmesh.libdtn.core.routing;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import io.left.rightmesh.libdtn.common.data.BundleID;
import io.left.rightmesh.libdtn.core.BaseComponent;
import io.left.rightmesh.libdtn.core.DTNCore;
import io.left.rightmesh.libdtn.modules.DeliveryAPI;
import io.left.rightmesh.libdtn.core.processor.EventListener;
import io.left.rightmesh.libdtn.common.data.Bundle;
import io.left.rightmesh.libdtn.core.events.RegistrationActive;
import io.left.rightmesh.libdtn.modules.aa.ActiveRegistrationCallback;
import io.left.rightmesh.libdtn.modules.RegistrarAPI;
import io.left.rightmesh.librxbus.Subscribe;
import io.reactivex.Completable;
import io.reactivex.Flowable;

import static io.left.rightmesh.libdtn.core.DTNConfiguration.Entry.COMPONENT_ENABLE_AA_REGISTRATION;


/**
 * RegistrationAPI Routing keeps track of the registered application agent.
 *
 * @author Lucien Loiseau on 24/08/18.
 */
public class AARegistrar extends BaseComponent implements RegistrarAPI, DeliveryAPI {

    private static final String TAG = "AARegistrar";

    public class Registration {
        String registeredSink;
        String cookie;
        ActiveRegistrationCallback cb;

        boolean isActive() {
            return cb != passiveRegistration;
        }

        Registration(String sink, ActiveRegistrationCallback cb) {
            this.registeredSink = sink;
            this.cb = cb;
            this.cookie = UUID.randomUUID().toString();
        }
    }

    private DTNCore core;
    private Map<String, Registration> registrations;
    private DeliveryListener listener;

    public AARegistrar(DTNCore core) {
        this.core = core;
        registrations = new ConcurrentHashMap<>();
        listener = new DeliveryListener(core);
        initComponent(core.getConf(), COMPONENT_ENABLE_AA_REGISTRATION, core.getLogger());
    }

    @Override
    public String getComponentName() {
        return TAG;
    }

    @Override
    protected void componentUp() {
    }

    @Override
    protected void componentDown() {
    }


    /* ---- helper method ---- */

    private void checkEnable() throws RegistrarDisabled {
        if(!isEnabled()) {
            throw new RegistrarDisabled();
        }
    }

    private void checkArgumentNotNull(Object obj) throws NullArgument {
        if(obj == null) {
            throw new NullArgument();
        }
    }

    private Registration checkRegisteredSink(String sink, String cookie) throws RegistrarDisabled, SinkNotRegistered, BadCookie, NullArgument {
        checkEnable();
        checkArgumentNotNull(sink);
        checkArgumentNotNull(cookie);
        Registration registration = registrations.get(sink);
        if(registration == null) {
            throw new SinkNotRegistered();
        }
        if(!registration.cookie.equals(cookie)) {
            throw new BadCookie();
        }
        return registration;
    }



    /* ------  RegistrarAPI  ------- */

    @Override
    public boolean isRegistered(String sink) throws RegistrarDisabled, NullArgument {
        checkEnable();
        checkArgumentNotNull(sink);
        return registrations.containsKey(sink);
    }

    @Override
    public String register(String sink)
            throws RegistrarDisabled, SinkAlreadyRegistered, NullArgument {
        return register(sink, passiveRegistration);
    }

    @Override
    public String register(String sink, ActiveRegistrationCallback cb)
            throws RegistrarDisabled, SinkAlreadyRegistered, NullArgument{
        checkEnable();
        checkArgumentNotNull(sink);
        checkArgumentNotNull(cb);

        Registration registration = new Registration(sink, cb);
        if (registrations.putIfAbsent(sink, registration) == null) {
            return registration.cookie;
        }

        throw new SinkAlreadyRegistered();
    }

    @Override
    public boolean unregister(String sink, String cookie) throws RegistrarDisabled, SinkNotRegistered, BadCookie, NullArgument {
        checkRegisteredSink(sink, cookie);
        if(registrations.remove(sink) == null) {
            throw new SinkNotRegistered();
        }
        return true;
    }

    @Override
    public boolean send(String sink, String cookie, Bundle bundle)  throws RegistrarDisabled, BadCookie, SinkNotRegistered, NullArgument {
        checkRegisteredSink(sink, cookie);
        checkArgumentNotNull(bundle);
        core.getBundleProcessor().bundleDispatching(bundle);
        return true;
    }

    @Override
    public Set<BundleID> checkInbox(String sink, String cookie) throws RegistrarDisabled, BadCookie, SinkNotRegistered, NullArgument {
        checkRegisteredSink(sink, cookie);
        // call storage service
        return null;
    }

    @Override
    public Bundle get(String sink, String cookie, BundleID bundleID) throws RegistrarDisabled, BadCookie, SinkNotRegistered, NullArgument {
        checkRegisteredSink(sink, cookie);
        checkArgumentNotNull(bundleID);
        // call storage service
        return null;
    }

    @Override
    public Bundle fetch(String sink, String cookie, BundleID bundleID) throws RegistrarDisabled, BadCookie, SinkNotRegistered, NullArgument {
        checkRegisteredSink(sink, cookie);
        checkArgumentNotNull(bundleID);
        return null;
    }

    @Override
    public Flowable<Bundle> fetch(String sink, String cookie) throws RegistrarDisabled, BadCookie, SinkNotRegistered, NullArgument {
        checkRegisteredSink(sink, cookie);
        return null;
    }

    @Override
    public boolean setActive(String sink, String cookie, ActiveRegistrationCallback cb) throws RegistrarDisabled, BadCookie, SinkNotRegistered, NullArgument {
        checkArgumentNotNull(cb);
        Registration registration = checkRegisteredSink(sink, cookie);
        registration.cb = cb;
        return true;
    }

    @Override
    public boolean setPassive(String sink, String cookie) throws RegistrarDisabled, BadCookie, SinkNotRegistered, NullArgument {
        Registration registration = checkRegisteredSink(sink, cookie);
        registration.cb = passiveRegistration;
        return true;
    }

    /**
     * print the state of the registration table
     *
     * @return String
     */
    public String printTable() {
        StringBuilder sb = new StringBuilder("\n\ncurrent registration table:\n");
        sb.append("---------------------------\n\n");
        if (isEnabled()) {
            registrations.forEach(
                    (sink, reg) -> {
                        sb.append(sink).append(" ");
                        if(reg.cb == passiveRegistration){
                            sb.append("PASSIVE\n");
                        } else {
                            sb.append("ACTIVE\n");
                        }
                    }
            );
        } else {
            sb.append("disabled");
        }
        sb.append("\n");
        return sb.toString();
    }

    /* ------  DeliveryAPI  ------- */

    public class DeliveryListener extends EventListener<String> {
        DeliveryListener(DTNCore core) {
            super(core);
        }

        @Override
        public String getComponentName() {
            return "DeliveryListener";
        }

        @Subscribe
        public void onEvent(RegistrationActive event) {
            /* deliver every bundle of interest */
            getBundlesOfInterest(event.sink).subscribe(
                    bundleID -> {
                        /* retrieve the bundle - should be constant operation */
                        core.getStorage().getMeta(bundleID).subscribe(
                                /* deliver it */
                                bundle -> event.cb.recv(bundle).subscribe(
                                        () -> {
                                            listener.unwatch(event.sink, bundle.bid);
                                            core.getBundleProcessor().bundleLocalDeliverySuccessful(bundle);
                                        },
                                        e -> core.getBundleProcessor().bundleLocalDeliveryFailure(event.sink, bundle)),
                                e -> {});
                    });
        }
    }

    /**
     * Deliver a bundle to the registration
     *
     * @param sink   registered
     * @param bundle to deliver
     * @return completes if the bundle was successfully delivered, onError otherwise
     */
    public Completable deliver(String sink, Bundle bundle) {
        if (!isEnabled()) {
            return Completable.error(new DeliveryDisabled());
        }

        Registration registration = registrations.get(sink);
        if(registration == null) {
            return Completable.error(new UnregisteredSink());
        }

        if(registration.cb == passiveRegistration) {
            return Completable.error(new PassiveRegistration());
        }

        return registration.cb.recv(bundle);
    }

    public void deliverLater(String sink, final Bundle bundle) {
        listener.watch(sink, bundle.bid);
    }

    /* passive registration */
    private static ActiveRegistrationCallback passiveRegistration = (payload) -> Completable.error(new PassiveRegistration());
}