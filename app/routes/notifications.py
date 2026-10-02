from fastapi import APIRouter, Depends, HTTPException
from app.auth import get_current_user
from app.database import notifications_collection
from bson import ObjectId

router = APIRouter(
    prefix="/notifications",
    tags=["Notifications"]
)

@router.get("")
def get_notifications(current_user: dict = Depends(get_current_user)):
    if current_user.get("role") != "candidate":
        raise HTTPException(status_code=403,detail="Only candidates can view these notifications")

    notifications = list(
        notifications_collection.find({
            "user_id": current_user["user_id"]
        }).sort("created_at", -1)
    )
    results = []

    for notification in notifications:
        results.append({
            "id": str(notification["_id"]),
            "message": notification["message"],
            "type": notification["type"],
            "read": notification["read"],
            "created_at": notification["created_at"]
        })

    return results

@router.patch("/{notification_id}/read")
def mark_notification_read(notification_id: str,current_user: dict = Depends(get_current_user)):
    if current_user.get("role") != "candidate":
        raise HTTPException(status_code=403,detail="Only candidates can update these notifications")
    if not ObjectId.is_valid(notification_id):
        raise HTTPException(status_code=400,detail="Invalid notification ID")
    
    result = notifications_collection.update_one(
        {
            "_id": ObjectId(notification_id),
            "user_id": current_user["user_id"]
        },
        {
            "$set": {"read": True}
        }
    )

    if result.matched_count == 0:
        raise HTTPException(
            status_code=404,
            detail="Notification not found"
        )

    return {"message": "Notification marked as read"}