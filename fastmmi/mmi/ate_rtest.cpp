#include <stdio.h>
#include <string.h>
#include <sys/types.h>
#include <errno.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <termios.h>
#include <stdlib.h>
#include "utils.h"
int set_opt(int fd,int nSpeed, int nBits, char nEvent, int nStop)
{
    struct termios newtio,oldtio;
    if  ( tcgetattr( fd,&oldtio)  !=  0) 
    { 
        ALOGE("SetupSerial 1");
        return -1;
    }
    bzero( &newtio, sizeof( newtio ) );
    newtio.c_cflag |= CLOCAL | CREAD; 
    newtio.c_cflag &= ~CSIZE; 

    switch(nBits){
    case 7:
        newtio.c_cflag |= CS7;
        break;
    case 8:
        newtio.c_cflag |= CS8;
        break;
    }

    switch(nEvent){
    case 'O':                     
        newtio.c_cflag |= PARENB;
        newtio.c_cflag |= PARODD;
        newtio.c_iflag |= (INPCK | ISTRIP);
        break;
    case 'E':                     
        newtio.c_iflag |= (INPCK | ISTRIP);
        newtio.c_cflag |= PARENB;
        newtio.c_cflag &= ~PARODD;
        break;
    case 'N':                    
        newtio.c_cflag &= ~PARENB;
        break;
    }

    switch(nSpeed){
    case 2400:
        cfsetispeed(&newtio, B2400);
        cfsetospeed(&newtio, B2400);
        break;
    case 4800:
        cfsetispeed(&newtio, B4800);
        cfsetospeed(&newtio, B4800);
        break;
    case 9600:
        cfsetispeed(&newtio, B9600);
        cfsetospeed(&newtio, B9600);
        break;
    case 115200:
        cfsetispeed(&newtio, B115200);
        cfsetospeed(&newtio, B115200);
        break;
    default:
        cfsetispeed(&newtio, B9600);
        cfsetospeed(&newtio, B9600);
        break;
    }
    if(nStop == 1){
        newtio.c_cflag &=  ~CSTOPB;
    }else if(nStop == 2){
        newtio.c_cflag |=  CSTOPB;
    }
    newtio.c_cc[VTIME]  = 0;
    newtio.c_cc[VMIN] = 0;
    tcflush(fd,TCIFLUSH);
    if((tcsetattr(fd,TCSANOW,&newtio))!=0){
        ALOGE("com set error");
        return -1;
    }
    ALOGI("set done!\n");
    return 0;
}

int open_port(int comport)
{
    //char *dev[3]={"/dev/smd11","/dev/ttyGS0","/dev/ttyHSL0"};
    long  vdisable;
    int fd = -1; 
    if(comport == 0){
       fd = open( "/dev/ttyHSL0", O_RDWR|O_NOCTTY|O_NDELAY);
    }else if(comport == 1){
       fd = open( "/dev/ttyHSL1", O_RDWR|O_NOCTTY|O_NDELAY);
    }else{
       fd = open( "/dev/ttyGS0", O_RDWR|O_NOCTTY|O_NDELAY);
    }
    if(fd < 0){
        ALOGE("Can't Open Serial Port,error=%s",strerror(errno));
        return -1;
    }else{
        //ALOGI("open %s success.....\n",dev[fd]);
    }
    
    if(fcntl(fd, F_SETFL, 0)<0){
        ALOGE("fcntl failed!\n");
    }else{
        ALOGI("fcntl=%d\n",fcntl(fd, F_SETFL,0));
    }
    if(isatty(STDIN_FILENO)==0){
        ALOGE("standard input is not a terminal device\n");
    }else{
        ALOGI("isatty success!\n");
    }
    ALOGI("fd-open=%d\n",fd);
    return fd;
}

int ate_main(int port_option){
    int port_num = -1;
    int fd,i;
    port_num = port_option;
    ALOGI("port num option is %d", port_num);
    if((fd=open_port(port_num))<0){
        ALOGE("open_port error");
        return -1;
    }
    if((i=set_opt(fd,115200,8,'N',1))<0){
        ALOGE("set_opt error");
        close(fd);
        return -1;
    }
    return fd;
}

