package com.example.mysmscode

import com.example.mysmscode.domain.ForwardAttemptStatus
import com.example.mysmscode.domain.RobotType
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationTextResourceMappingTest {

    @Test
    fun notificationStatusLabelRes_mapsKnownStatusesToChineseResources() {
        assertEquals(R.string.notification_forward_status_success, notificationForwardStatusLabelRes(ForwardAttemptStatus.SUCCESS))
        assertEquals(R.string.notification_forward_status_failed, notificationForwardStatusLabelRes(ForwardAttemptStatus.FAILED))
        assertEquals(R.string.notification_forward_status_pending, notificationForwardStatusLabelRes(ForwardAttemptStatus.PENDING))
    }

    @Test
    fun notificationRobotTypeLabelRes_mapsKnownRobotTypesToChineseResources() {
        assertEquals(R.string.robot_type_feishu, notificationRobotTypeLabelRes(RobotType.FEISHU))
        assertEquals(R.string.robot_type_wecom, notificationRobotTypeLabelRes(RobotType.WECOM))
    }
}
