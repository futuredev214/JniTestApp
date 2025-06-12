// native_bt.cpp
#include <jni.h>
#include <string>
#include <android/log.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/socket.h>
#include "../include/bluetooth.h"
#include "../include/rfcomm.h"
#include "dbus/dbus.h"

#define LOG_TAG "NativeBT"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// 🔧 ba2str 대체 구현
void ba2strMac(const bdaddr_t *ba, char *str) {
    snprintf(
    str, 18,
    "%02X:%02X:%02X:%02X:%02X:%02X",
        ba->b[5], ba->b[4], ba->b[3],
        ba->b[2], ba->b[1], ba->b[0]);
}

extern "C"
JNIEXPORT jint JNICALL
Java_kr_co_mirerotack_btsever1_MainActivity_00024NativeBtServer_startBluetoothServer(JNIEnv *env, jobject thiz) {
    int sockfd, client;
    struct sockaddr_rc loc_addr = {0}, rem_addr = {0};
    char buf[1024] = {0};
    socklen_t opt = sizeof(rem_addr);

    // 1. 소켓 생성
    sockfd = socket(AF_BLUETOOTH, SOCK_STREAM, BTPROTO_RFCOMM);
    if (sockfd < 0) {
        LOGE("소켓 생성 실패");
        return -1;
    } else {
        LOGE("소켓 생성 성공");
    }

    // 2. 서버 주소 설정
    loc_addr.rc_family = AF_BLUETOOTH;

    bdaddr_t any = {{0, 0, 0, 0, 0, 0}};
    loc_addr.rc_bdaddr = any;

    loc_addr.rc_channel = (uint8_t)1;

    // 3. 바인드
    if (bind(sockfd, (struct sockaddr *)&loc_addr, sizeof(loc_addr)) < 0) {
        LOGE("바인드 실패");
        close(sockfd);
        return -2;
    } else {
        LOGE("바인드 성공");
    }

    // 4. 리슨
    if (listen(sockfd, 1) < 0) {
        LOGE("리슨 실패");
        close(sockfd);
        return -3;
    } else {
        LOGE("리슨 성공");
    }

    LOGI("클라이언트 연결 대기 중...");

    // 5. accept (블로킹)
    client = accept(sockfd, (struct sockaddr *)&rem_addr, &opt);
    if (client < 0) {
        LOGE("클라이언트 수락 실패");
        close(sockfd);
        return -4;
    }

    char addr[18] = {0};
    ba2strMac(&rem_addr.rc_bdaddr, addr);
    LOGI("클라이언트 연결됨: %s", addr);

    // 6. 예시 메시지 수신
    int bytes_read = read(client, buf, sizeof(buf));
    if (bytes_read > 0) {
        LOGI("수신된 메시지: %s", buf);
    }

    close(client);
    close(sockfd);
    return 0;
}