import { Component, OnInit } from '@angular/core';
import { Router, UrlTree, NavigationEnd } from '@angular/router';
import * as moment from 'moment';
import { NotificationService } from 'app/shared/notification/notification.service';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { Notification } from 'app/entities/notification.model';

@Component({
    selector: 'jhi-notification-popup',
    templateUrl: './notification-popup.component.html',
    styleUrls: ['./notification-popup.scss'],
})
export class NotificationPopupComponent implements OnInit {
    notifications: Notification[] = [];

    constructor(private accountService: AccountService, private notificationService: NotificationService, private router: Router) {}

    /**
     * Subscribe to notification updates that are received via websocket if the user is logged in.
     */
    ngOnInit(): void {
        this.accountService.getAuthenticationState().subscribe((user: User | null) => {
            if (user) {
                this.subscribeToNotificationUpdates();
            }
        });
    }

    /**
     * Returns a string that can be interpreted at fontawesome icon based on the notification type of the given notification.
     * @param notification {Notification}
     */
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    notificationIcon(notification: Notification): string {
        return 'check-double';
    }

    /**
     * Removes the notification at the specified index from the notifications array.
     * @param index {number}
     */
    removeNotification(index: number): void {
        this.notifications.splice(index, 1);
    }

    /**
     * Navigate to the target (view) of the notification that the user clicked.
     * @param notification {Notification}
     */
    navigateToTarget(notification: Notification): void {
        this.router.navigateByUrl(this.notificationTargetRoute(notification));
    }

    private notificationTargetRoute(notification: Notification): UrlTree {
        const target = JSON.parse(notification.target);
        return this.router.createUrlTree([target.mainPage, target.course, target.entity, target.id]);
    }

    private subscribeToNotificationUpdates(): void {
        this.notificationService.subscribeToSocketMessages().subscribe((notification: Notification) => {
            if (notification && notification.notificationDate) {
                this.addNotification(notification);
            }
        });
    }

    private addNotification(notification: Notification): void {
        notification.notificationDate = moment(notification.notificationDate);
        // Only add a notification if it does not already exist
        if (!this.notifications.some(({ id }) => id === notification.id)) {
            // For now only notifications about a started quiz should be displayed
            if (notification.title === 'Quiz started') {
                this.addQuizNotification(notification);
            }
        }
    }

    /**
     * Will add a notification about a started quiz to the component's state.
     * The notification will only be added if the user is not already on the target page.
     * @param notification {Notification}
     */
    private addQuizNotification(notification: Notification): void {
        if (!this.router.isActive(this.notificationTargetRoute(notification), true)) {
            this.notifications.unshift(notification);
        }
    }
}
