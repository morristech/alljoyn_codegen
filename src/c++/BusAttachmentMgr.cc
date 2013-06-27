/******************************************************************************
@License@
 *
 * BusAttachmentMgr.cc
 * This file contains implementation of the BusAttachmentMgr class.
 *
 *****************************************************************************/
#include <signal.h>
#include "BusAttachmentMgr.h"
#include <alljoyn/AllJoynStd.h>
#include <alljoyn/DBusStd.h>

/*-----------------------------------------------------------------------------
  METHOD: BusAttachmentMgr()
  Constructor for the Bus Attachment manager.  Takes the application name as
  an argument.
-----------------------------------------------------------------------------*/
BusAttachmentMgr::BusAttachmentMgr(const char *applicationName, const char *wellKnownName, 
    bool enableDiscovery):
    discoveryEnabled(enableDiscovery)
{
    myBusAttachment = new BusAttachment(applicationName, enableDiscovery);
    
    myBusListener = new MyBusListener(*myBusAttachment, wellKnownName);

} /* BusAttachmentMgr() */

/*-----------------------------------------------------------------------------
  METHOD: RegisterBusObject()
  Method call to register the service with the bus - this will expose that
      Service and make it available to clients for use.
-----------------------------------------------------------------------------*/
QStatus BusAttachmentMgr::RegisterBusObject(BusObject& obj) {
    
    QStatus status = myBusAttachment->RegisterBusObject(obj);
    return status;

} /* RegisterBusObject() */

/*-----------------------------------------------------------------------------
  METHOD: GetBusAttachment()
  Return a reference to the bus attachment object: this is needed when
  defining a new service object.
-----------------------------------------------------------------------------*/
BusAttachment* BusAttachmentMgr::GetBusAttachment() {
    
    return myBusAttachment;

} /* GetBusAttachment() */

/*-----------------------------------------------------------------------------
  METHOD: GetBusListener()
  Return a reference to the bus listener object: this is needed when
  using a session ID.
-----------------------------------------------------------------------------*/
MyBusListener* BusAttachmentMgr::GetBusListener() {
    
    return myBusListener;

} /* GetBusListener() */

/*-----------------------------------------------------------------------------
  METHOD: StartClient()
  Code to start a bus object.  Is reused by StartService().
-----------------------------------------------------------------------------*/
QStatus BusAttachmentMgr::StartClient() {

    QStatus status = ER_OK;

    /* Get env vars */
    const char* connectSpec = getenv("BUS_ADDRESS");
    if (connectSpec == NULL) {
#ifdef _WIN32
        connectSpec = "tcp:addr=127.0.0.1,port=9955";
#else
        connectSpec = "unix:abstract=alljoyn";
#endif
    }
    
    /* Start and connect the bus attachment to the message bus */
    status = myBusAttachment->Start();
    if(status != ER_OK) {
        printf("BusAttachment::Start failed\n");
    } else {
        myBusAttachment->RegisterBusListener(*myBusListener);
        
        status = myBusAttachment->Connect(connectSpec);
        if(status != ER_OK) {
            printf("Failed to connect to \"%s\"\n", connectSpec);
        }
    } /* if(start() succeeded) */

    return status;

} /* StartClient() */

/*-----------------------------------------------------------------------------
  METHOD: StartService()
  Uses StartClient() to connect to the bus.
-----------------------------------------------------------------------------*/
QStatus BusAttachmentMgr::StartService(const char* advertisedName) {

    QStatus status = StartClient();

    status = myBusAttachment->RequestName(advertisedName, DBUS_NAME_FLAG_DO_NOT_QUEUE);
    if (ER_OK != status) {
        printf("RequestName(%s) failed (status=%s)\n", advertisedName, QCC_StatusText(status));
        status = (status == ER_OK) ? ER_FAIL : status;
    }

    SessionOpts opts(SessionOpts::TRAFFIC_MESSAGES, true, SessionOpts::PROXIMITY_ANY, TRANSPORT_ANY);
    if (ER_OK == status) {
        SessionPort sp = myBusListener->SESSION_PORT;
        status = myBusAttachment->BindSessionPort(sp, opts, *myBusListener);
        if (ER_OK != status) {
            printf("BindSessionPort failed (%s)\n", QCC_StatusText(status));
        }
    }

    if (ER_OK == status) {
        status = myBusAttachment->AdvertiseName(advertisedName, opts.transports);
        if (ER_OK != status) {
            printf("Failed to advertise name %s (%s)\n", advertisedName, QCC_StatusText(status));
        }
    }

    return status;

} /* StartService() */

/*-----------------------------------------------------------------------------
  METHOD: Stop()
  Stop and disconnect the bus attachment.
-----------------------------------------------------------------------------*/
QStatus BusAttachmentMgr::Stop() {
    QStatus status = ER_OK;

    status = myBusAttachment->Stop();
    if (ER_OK == status) {
        /* The join will block until all threads have completed. */
        status = myBusAttachment->Join();

        if (ER_OK != status) {
            printf("BusAttachment::Join() failed\n");
        }
    } else {
        printf("BusAttachment::Stop() failed\n");
    }

    return status;

} /* Stop() */

/*-----------------------------------------------------------------------------
  METHOD: Delete()
  Stop and Delete the bus attachment
-----------------------------------------------------------------------------*/
void BusAttachmentMgr::Delete() {

    /* Stop, then Deallocate bus */
    if (ER_OK == Stop()) {
        BusAttachment* deleteMe = myBusAttachment;
        myBusAttachment = NULL;
        delete deleteMe;
        
        if (NULL != myBusListener) {
            delete myBusListener;
            myBusListener = NULL;
        }
    } /* if(stop succeeded) */

} /* Delete() */

/*-------------------------------------------------------------------------
  METHOD: DiscoveryEnabled()
  find out if global discovery has been enabled.
-------------------------------------------------------------------------*/
bool BusAttachmentMgr::DiscoveryEnabled(){
    return discoveryEnabled;
} /* DiscoveryEnabled() */

static volatile sig_atomic_t s_interrupt = false;
static bool s_handlerInstalled = false;

static void SigIntHandler(int sig)
{
    s_interrupt = true;
}

/*-------------------------------------------------------------------------
  METHOD: WaitForSigInt()
  Wait for the interrupt signal.
-------------------------------------------------------------------------*/
void BusAttachmentMgr::WaitForSigInt(void)
{
    if(!s_handlerInstalled) {
        s_handlerInstalled = true;

        /* Install SIGINT handler */
        signal(SIGINT, SigIntHandler);
    }

    while (s_interrupt == false) {
#ifdef _WIN32
        Sleep(100);
#else
        usleep(100 * 1000);
#endif
    }
}

MyBusListener::MyBusListener(BusAttachment &bus, qcc::String name):
        advertizedName(name),
        nameFound(false)
{
    myBusAttachment = &bus;
} /* MyBusListener */


void MyBusListener::FoundAdvertisedName(const char* name, TransportMask transport, const char* namePrefix)
{
    printf("Discovered Advertised Name: \"%s\"\n", name);

    /* Since we are in a callback we must enable concurrent callbacks before calling a synchronous method. */
    myBusAttachment->EnableConcurrentCallbacks();

    /* Join the Session */
    SessionOpts opts(SessionOpts::TRAFFIC_MESSAGES, true, SessionOpts::PROXIMITY_ANY, TRANSPORT_ANY);
    QStatus status = myBusAttachment->JoinSession(name, SESSION_PORT, this, mySessionID, opts);
    if (ER_OK == status) {
        printf("Joined Session \"%s\" with SessionID 0x%x\n", name, mySessionID);
        nameFound = true;
    } else {
        printf("JoinSession failed (status=%s)\n", QCC_StatusText(status));
    }
}

void MyBusListener::NameOwnerChanged(const char* name, const char* previousOwner, const char* newOwner)
{
    //if (newOwner && (0 == strcmp(name, advertizedName.c_str()))) {
    //    printf("NameOwnerChanged(%s, %s, %s)\n",
    //           name,
    //           previousOwner ? previousOwner : "null",
    //           newOwner ? newOwner : "null");
    //}
} /* NameOwenerChanged() */

bool MyBusListener::AcceptSessionJoiner(SessionPort sessionPort, const char* joiner, const SessionOpts& opts)
{
    if (sessionPort != SESSION_PORT) {
        printf("Rejecting join attempt on non-chat session port %d\n", sessionPort);
        return false;
    }

    printf("Accepting join session request from %s (opts.proximity=%x, opts.traffic=%x, opts.transports=%x)\n",
           joiner, opts.proximity, opts.traffic, opts.transports);
    return true;
}

void MyBusListener::SessionJoined(SessionPort sessionPort, SessionId id, const char* joiner)
{
    mySessionID = id;
    printf("SessionJoined with %s (id=%d)\n", joiner, id);
}
