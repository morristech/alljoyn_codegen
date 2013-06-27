#ifndef _BUS_ATTACHMENT_MGR_H
#define _BUS_ATTACHMENT_MGR_H

/******************************************************************************
@License@
 *
 * BusAttachmentMgr.h
 * This file defines the class "BusAttachmentMgr". This class contains wrapper
 * functions that makes very basic interactions with the BusAttachment class
 * easier. The class has methods that handles start, stop, delete, and
 * registering a busObject on the bus. This class only handles the minimal
 * interaction with AllJoyn, advanced developers should try to understand the
 * BusAttachment class in order to use its more advanced features.
 * 
 ******************************************************************************/

#include <qcc/String.h>
#include <stdio.h>
#include <alljoyn/BusAttachment.h>
#include <alljoyn/AllJoynStd.h>
#include <alljoyn/Session.h>

using namespace ajn;

/* forward declaration */
class MyBusListener;

class BusAttachmentMgr {
  public:
    /*-------------------------------------------------------------------------
      METHOD: BusAttachmentMgr()
      Constructor for the Bus Attachment manager.  Takes the application
      name as an argument.
    -------------------------------------------------------------------------*/
    BusAttachmentMgr(const char *applicationName, const char *wellKnownName, bool enableDiscovery);

    /*-------------------------------------------------------------------------
      METHOD: RegisterBusObject()
      Method call to register a service with the bus - this will expose that
      Service and make it available to clients for use.
    -------------------------------------------------------------------------*/
    QStatus RegisterBusObject(BusObject& obj);

    /*-------------------------------------------------------------------------
      METHOD: GetBusAttachment()
      Return a reference to the bus attachment object: this is needed when
      defining a new service object.
    -------------------------------------------------------------------------*/
    BusAttachment* GetBusAttachment();

    /*-------------------------------------------------------------------------
      METHOD: GetBusListener()
      Return a reference to the bus listener object: this is needed when
      using a session ID.
    -------------------------------------------------------------------------*/
    MyBusListener* GetBusListener();
    
    /*-------------------------------------------------------------------------
      METHOD: StartClient()
      Code to start a bus object.  Is reused by StartService().
    -------------------------------------------------------------------------*/
    QStatus StartClient();

    /*-------------------------------------------------------------------------
      METHOD: StartService()
      Uses StartClient() to connect to the bus, then blocks until the service
      is stopped
    -------------------------------------------------------------------------*/
    QStatus StartService(const char* advertisedName);

    /*-------------------------------------------------------------------------
      METHOD: Stop()
      Stop and disconnect the bus attachment.
    -------------------------------------------------------------------------*/
    QStatus Stop();

    /*-------------------------------------------------------------------------
      METHOD: Delete()
      Stop and Delete the bus attachment
    -------------------------------------------------------------------------*/
    void Delete();

    /*-------------------------------------------------------------------------
      METHOD: DiscoveryEnabled()
      Find out if global discovery has been enabled.
    -------------------------------------------------------------------------*/
    bool DiscoveryEnabled();

    /*-------------------------------------------------------------------------
      METHOD: WaitForSigInt()
      Wait for the interrupt signal.
    -------------------------------------------------------------------------*/
    void WaitForSigInt(void);

  private:
    /*-------------------------------------------------------------------------
      MEMBER: myBusAttachment
      Reference to the bus attachment that the is managing
    -------------------------------------------------------------------------*/
    BusAttachment* myBusAttachment;
    MyBusListener* myBusListener;
    bool discoveryEnabled;
};

/* AllJoynListener receives discovery events from AllJoyn */
class MyBusListener : public BusListener, public SessionPortListener, public SessionListener
{

  public :

    MyBusListener(BusAttachment &bus, qcc::String name);

    void FoundAdvertisedName(const char* name, TransportMask transport, const char* namePrefix);
    
    void NameOwnerChanged(const char* name, const char* previousOwner, const char* newOwner);

    bool AcceptSessionJoiner(SessionPort sessionPort, const char* joiner, const SessionOpts& opts);
    
    void SessionJoined(SessionPort sessionPort, SessionId id, const char* joiner);
    
    BusAttachment *myBusAttachment;
    qcc::String advertizedName;
    bool nameFound;
    SessionId mySessionID;
    /* SESSION_PORT can be set to any number from 1 to 0xFFFF */
    static const SessionPort SESSION_PORT = 24;
};

#endif /* _BUS_ATTACHMENT_MGR_H */
