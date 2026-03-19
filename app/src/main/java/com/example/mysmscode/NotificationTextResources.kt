package com.example.mysmscode

import androidx.annotation.StringRes
import com.example.mysmscode.domain.ForwardAttemptStatus
import com.example.mysmscode.domain.RobotType

@StringRes
fun notificationForwardStatusLabelRes(status: ForwardAttemptStatus): Int = when (status) {
    ForwardAttemptStatus.PENDING -> R.string.notification_forward_status_pending
    ForwardAttemptStatus.SUCCESS -> R.string.notification_forward_status_success
    ForwardAttemptStatus.FAILED -> R.string.notification_forward_status_failed
}

@StringRes
fun notificationRobotTypeLabelRes(type: RobotType): Int = when (type) {
    RobotType.FEISHU -> R.string.robot_type_feishu
    RobotType.WECOM -> R.string.robot_type_wecom
}
