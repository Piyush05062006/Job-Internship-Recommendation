from fastapi import APIRouter, Depends, HTTPException

from app.database import (profiles_collection,jobs_collection)

from app.auth import get_current_user
from app.recommendation import calculate_match

router = APIRouter(
    prefix="/recommendations",
    tags=["Recommendations"]
)


@router.get("")
def get_recommendations(current_user: dict = Depends(get_current_user)):
    if current_user["role"] != "candidate":
        raise HTTPException(
            status_code=403,
            detail="Only candidates can view recommendations"
        )
    profile = profiles_collection.find_one({
        "user_id": current_user["user_id"]
    })

    if not profile:
        raise HTTPException(
            status_code=404,
            detail="Please create your profile first"
        )
    
    jobs = jobs_collection.find({
        "status": "open"
    })

    recommendations = []

    # Calculate match for every job
    for job in jobs:
        match = calculate_match(profile, job)
        recommendations.append({
            "job_id": str(job["_id"]),
            "title": job["title"],
            "company": job["company"],
            "location": job["location"],
            "job_type": job["job_type"],
            "required_skills": job["required_skills"],
            **match
        })

    recommendations.sort(
        key=lambda job: job["match_percentage"],
        reverse=True
    )
    return recommendations