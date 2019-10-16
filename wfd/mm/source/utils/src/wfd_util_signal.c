/* =======================================================================
                            wfd_util_signal.c
DESCRIPTION
  This header defines the wfd source item class.

EXTERNALIZED FUNCTIONS
  None

INITIALIZATION AND SEQUENCING REQUIREMENTS

Copyright (c) 2011, 2015-2016 Qualcomm Technologies, Inc.
All Rights Reserved.
Confidential and Proprietary - Qualcomm Technologies, Inc.
========================================================================== */

/*========================================================================
Include Files
==========================================================================*/
#include "wfd_util_signal.h"
#include "MMDebugMsg.h"
#include <time.h>
#include <pthread.h>
#include <errno.h>
#include "MMMalloc.h"

#ifdef TRUE
#undef TRUE
#endif

#ifdef FALSE
#undef FALSE
#endif

#define TRUE   1   /* Boolean true value. */
#define FALSE  0   /* Boolean false value. */
#define NS_PER_S 1000000000

typedef struct venc_signal_type
{
  pthread_cond_t cond;
  pthread_mutex_t mutex;
  unsigned char m_bSignalSet ;
} venc_signal_type;



/////////////////////////////////////////////////////////////////////////////
/////////////////////////////////////////////////////////////////////////////
int venc_signal_create(void** handle)
{
  int result = 0;

  if (handle)
  {
    venc_signal_type* sig;

    *handle = (void *)MM_Malloc(sizeof(venc_signal_type));
    sig = (venc_signal_type*) *handle;
    if(sig != NULL)
    {
        sig->m_bSignalSet = FALSE ;
    }
    if (*handle)
    {
      if (pthread_cond_init(&sig->cond,NULL) == 0)
      {
        if (pthread_mutex_init(&sig->mutex, NULL) != 0)
        {
          MM_MSG_PRIO(MM_GENERAL,MM_PRIO_ERROR,"failed to create mutex");
          result = 1;
          pthread_cond_destroy(&sig->cond);
          MM_Free(*handle);
        }
      }
      else
      {
        MM_MSG_PRIO(MM_GENERAL,MM_PRIO_ERROR,"failed to create cond var");
        result = 1;
        MM_Free(*handle);
      }
    }
    else
    {
      MM_MSG_PRIO(MM_GENERAL,MM_PRIO_ERROR,"failed to alloc handle");
      result = 1;
    }
  }
  else
  {
    MM_MSG_PRIO(MM_GENERAL,MM_PRIO_ERROR,"handle is null");
    result = 1;
  }

  return result;
}

/////////////////////////////////////////////////////////////////////////////
/////////////////////////////////////////////////////////////////////////////
int venc_signal_destroy(void* handle)
{
  int result = 0;

  if (handle)
  {
    venc_signal_type* sig = (venc_signal_type*) handle;
    sig->m_bSignalSet = FALSE ;
    pthread_cond_destroy(&sig->cond);
    pthread_mutex_destroy(&sig->mutex);
    free(handle);
  }
  else
  {
    MM_MSG_PRIO(MM_GENERAL,MM_PRIO_ERROR,"handle is null");
    result = 1;
  }

  return result;
}

/////////////////////////////////////////////////////////////////////////////
/////////////////////////////////////////////////////////////////////////////
int venc_signal_set(void* handle)
{
  int result = 0;

  if (handle)
  {
    venc_signal_type* sig = (venc_signal_type*) handle;

    if (pthread_mutex_lock(&sig->mutex) == 0)
    {
      sig->m_bSignalSet = TRUE ;
      if (pthread_cond_signal(&sig->cond) != 0)
      {
        MM_MSG_PRIO(MM_GENERAL,MM_PRIO_ERROR,"error setting signal");
        result = 1;
      }

      if (pthread_mutex_unlock(&sig->mutex) != 0)
      {
        MM_MSG_PRIO(MM_GENERAL,MM_PRIO_ERROR,"error unlocking mutex");
        result = 1;
      }
    }
    else
    {
      MM_MSG_PRIO(MM_GENERAL,MM_PRIO_ERROR,"error locking mutex");
      result = 1;
    }
  }
  else
  {
    MM_MSG_PRIO(MM_GENERAL,MM_PRIO_ERROR,"handle is null");
    result = 1;
  }

  return result;
}

/////////////////////////////////////////////////////////////////////////////
/////////////////////////////////////////////////////////////////////////////
int venc_signal_wait(void* handle, int timeout)
{
  int result = 0;

  if (handle)
  {
    venc_signal_type* sig = (venc_signal_type*) handle;

    if (pthread_mutex_lock(&sig->mutex) == 0)
    {

      if (timeout > 0)
      {
        int wait_result;
        struct timespec time;
        clock_gettime(CLOCK_REALTIME, &time);

        time.tv_sec += timeout / 1000;
        time.tv_nsec += (timeout % 1000) * 1000000;

        /* normalize the time. tv_nsec should not overflow */
        time.tv_sec += time.tv_nsec / NS_PER_S;
        time.tv_nsec %= NS_PER_S;

        wait_result = pthread_cond_timedwait(&sig->cond, &sig->mutex, &time);
        if (wait_result == ETIMEDOUT)
        {
          result = 2;
        }
        else if (wait_result != 0)
        {
          result = 1;
        }
      }
      else
      {
        if(sig->m_bSignalSet == TRUE)
        {
          struct timespec time;
          timeout = 1 ;
          clock_gettime(CLOCK_REALTIME, &time);
          time.tv_sec += timeout / 1000;
          time.tv_nsec += (timeout % 1000) * 1000000;
          MM_MSG_PRIO(MM_GENERAL,MM_PRIO_MEDIUM,"error waiting for signal but its already set");
          pthread_cond_timedwait(&sig->cond, &sig->mutex, &time) ;
          sig->m_bSignalSet = FALSE ;
        }
        else
        {
          if (pthread_cond_wait(&sig->cond, &sig->mutex) != 0)
          {
            MM_MSG_PRIO(MM_GENERAL,MM_PRIO_ERROR,"error waiting for signal");
            result = 1;
          }
          sig->m_bSignalSet = FALSE ;
        }
      }

      if (pthread_mutex_unlock(&sig->mutex) != 0)
      {
        MM_MSG_PRIO(MM_GENERAL,MM_PRIO_ERROR,"error unlocking mutex");
        result = 1;
      }

    }
    else
    {
      MM_MSG_PRIO(MM_GENERAL,MM_PRIO_ERROR,"error locking mutex");
      result = 1;
    }
  }
  else
  {
    MM_MSG_PRIO(MM_GENERAL,MM_PRIO_ERROR,"handle is null");
    result = 1;
  }

  return result;
}
