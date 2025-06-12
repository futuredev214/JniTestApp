////
//// Created by user on 2025-05-29.
////
//// bluetooth_agent.cpp
//#include "dbus/dbus.h"
//#include <stdio.h>
//#include <stdlib.h>
//#include <unistd.h>
//#include <string.h>
//
//#define AGENT_PATH "/com/mirerotack/agent"
//#define PASSKEY 123456
//
//DBusConnection* connection;
//
//static DBusHandlerResult agent_handler(DBusConnection *conn, DBusMessage *msg, void *data) {
//    if (dbus_message_is_method_call(msg, "org.bluez.Agent1", "RequestPasskey")) {
//        const char *device;
//        if (!dbus_message_get_args(msg, NULL, DBUS_TYPE_OBJECT_PATH, &device, DBUS_TYPE_INVALID)) {
//            fprintf(stderr, "[!] Failed to parse arguments\n");
//            return DBUS_HANDLER_RESULT_NOT_YET_HANDLED;
//        }
//        printf("[*] RequestPasskey from %s\n", device);
//
//        DBusMessage *reply = dbus_message_new_method_return(msg);
//        uint32_t passkey = PASSKEY;
//        dbus_message_append_args(reply, DBUS_TYPE_UINT32, &passkey, DBUS_TYPE_INVALID);
//        dbus_connection_send(conn, reply, NULL);
//        dbus_message_unref(reply);
//
//        return DBUS_HANDLER_RESULT_HANDLED;
//    }
//    return DBUS_HANDLER_RESULT_NOT_YET_HANDLED;
//}
//
//void register_agent() {
//    DBusMessage *msg, *reply;
//    DBusError err;
//    dbus_error_init(&err);
//
//    // System Bus 연결
//    connection = dbus_bus_get(DBUS_BUS_SYSTEM, &err);
//    if (dbus_error_is_set(&err)) {
//        fprintf(stderr, "[!] DBus connection error: %s\n", err.message);
//        dbus_error_free(&err);
//        exit(1);
//    }
//
//    // Agent Object 등록
//    static DBusObjectPathVTable vtable = {NULL, agent_handler, NULL, NULL, NULL, NULL};
//    dbus_connection_register_object_path(connection, AGENT_PATH, &vtable, NULL);
//
//    // Agent 등록 호출
//    msg = dbus_message_new_method_call("org.bluez", "/org/bluez", "org.bluez.AgentManager1", "RegisterAgent");
//    const char *capability = "DisplayYesNo";
//    dbus_message_append_args(msg, DBUS_TYPE_OBJECT_PATH, &AGENT_PATH,
//                             DBUS_TYPE_STRING, &capability, DBUS_TYPE_INVALID);
//
//    reply = dbus_connection_send_with_reply_and_block(connection, msg, -1, &err);
//    dbus_message_unref(msg);
//    if (dbus_error_is_set(&err)) {
//        fprintf(stderr, "[!] Failed to register agent: %s\n", err.message);
//        dbus_error_free(&err);
//        exit(1);
//    }
//    dbus_message_unref(reply);
//    printf("[+] Agent registered with passkey: %06d\n", PASSKEY);
//
//    // 기본 Agent 설정
//    msg = dbus_message_new_method_call("org.bluez", "/org/bluez", "org.bluez.AgentManager1", "RequestDefaultAgent");
//    dbus_message_append_args(msg, DBUS_TYPE_OBJECT_PATH, &AGENT_PATH, DBUS_TYPE_INVALID);
//    reply = dbus_connection_send_with_reply_and_block(connection, msg, -1, &err);
//    dbus_message_unref(msg);
//    if (dbus_error_is_set(&err)) {
//        fprintf(stderr, "[!] Failed to set default agent: %s\n", err.message);
//        dbus_error_free(&err);
//        exit(1);
//    }
//    dbus_message_unref(reply);
//    printf("[+] Default agent set\n");
//}
//
//int main() {
//    register_agent();
//    while (dbus_connection_read_write_dispatch(connection, -1)) {
//        // 메인 루프
//    }
//    return 0;
//}
