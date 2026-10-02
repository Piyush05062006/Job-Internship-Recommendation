from fastapi import APIRouter, Depends, HTTPException

from app.database import profiles_collection
from app.models import CandidateProfile
from app.auth import get_current_user


router = APIRouter(
    prefix="/profile",
    tags=["Candidate Profile"]
)


@router.put("")
def update_profile(profile: CandidateProfile,current_user: dict = Depends(get_current_user)):
    if current_user["role"] != "candidate":
        raise HTTPException(
            status_code=403,
            detail="Only candidates can create a profile"
        )

    profile_data = profile.model_dump()

    profile_data["user_id"] = current_user["user_id"]

    profiles_collection.update_one(
        {"user_id": current_user["user_id"]},
        {"$set": profile_data},
        upsert=True
    )

    return {
        "message": "Profile updated successfully"
    }


@router.get("")
def get_profile(
    current_user: dict = Depends(get_current_user)
):

    if current_user["role"] != "candidate":
        raise HTTPException(
            status_code=403,
            detail="Only candidates can access this profile"
        )

    profile = profiles_collection.find_one({
        "user_id": current_user["user_id"]
    })

    if not profile:
        raise HTTPException(
            status_code=404,
            detail="Profile not found"
        )

    profile["id"] = str(profile.pop("_id"))

    return profile