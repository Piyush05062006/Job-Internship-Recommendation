from datetime import datetime, timezone
from bson import ObjectId
from fastapi import APIRouter, Depends, HTTPException
from app.auth import get_current_user
from app.database import (
    applications_collection,
    jobs_collection,
    users_collection,
    notifications_collection,
)
from app.models import ApplicationStatusUpdate

router = APIRouter(
    prefix="/applications",
    tags=["Job Applications"]
)

def require_role(current_user: dict, role: str):
    if current_user.get("role") != role:
        raise HTTPException(
            status_code=403,
            detail=f"Only {role}s can access this endpoint"
        )


# Candidate applies for a job
@router.post("/{job_id}")
def apply_for_job(job_id: str,current_user: dict = Depends(get_current_user)):
    require_role(current_user, "candidate")
    if not ObjectId.is_valid(job_id):
        raise HTTPException(status_code=400,detail="Invalid job ID")
    
    job = jobs_collection.find_one({"_id": ObjectId(job_id),"status": "open"})

    if not job:
        raise HTTPException(status_code=404,detail="Open job not found")

    candidate_id = current_user["user_id"]

    existing_application = applications_collection.find_one({
        "candidate_id": candidate_id,
        "job_id": job_id
    })

    if existing_application:
        raise HTTPException(
            status_code=409,
            detail="You have already applied for this job"
        )

    application = {
        "candidate_id": candidate_id,
        "job_id": job_id,
        "recruiter_id": job["recruiter_id"],
        "status": "applied",
        "applied_at": datetime.now(timezone.utc).isoformat()
    }

    result = applications_collection.insert_one(application)

    return {
        "message": "Application submitted successfully",
        "application_id": str(result.inserted_id),
        "status": "applied"
    }

# Candidate can view their own applications
@router.get("/my")
def get_my_applications(current_user: dict = Depends(get_current_user)):
    require_role(current_user, "candidate")

    applications = list(
        applications_collection.find({
            "candidate_id": current_user["user_id"]
        }).sort("applied_at", -1)
    )

    results = []

    for application in applications:
        job = jobs_collection.find_one({
            "_id": ObjectId(application["job_id"])
        })
        results.append({
            "application_id": str(application["_id"]),
            "job_id": application["job_id"],
            "job_title": job.get("title", "Job no longer available") if job else "Job no longer available",
            "company": job.get("company", "") if job else "",
            "location": job.get("location", "") if job else "",
            "job_type": job.get("job_type", "") if job else "",
            "status": application["status"],
            "applied_at": application["applied_at"]
        })
    return results

# Recruiter views applications for their own jobs
@router.get("/recruiter")
def get_recruiter_applications(current_user: dict = Depends(get_current_user)):
    require_role(current_user, "recruiter")

    applications = list(
        applications_collection.find({
            "recruiter_id": current_user["user_id"]
        }).sort("applied_at", -1)
    )
    results = []
    for application in applications:
        candidate = users_collection.find_one(
            {"_id": ObjectId(application["candidate_id"])},
            {"name": 1, "email": 1}
        )
        job = jobs_collection.find_one({
            "_id": ObjectId(application["job_id"])
        })
        results.append({
            "application_id": str(application["_id"]),
            "candidate_id": application["candidate_id"],
            "candidate_name": candidate.get("name", "") if candidate else "",
            "candidate_email": candidate.get("email", "") if candidate else "",
            "job_id": application["job_id"],
            "job_title": job.get("title", "") if job else "",
            "company": job.get("company", "") if job else "",
            "status": application["status"],
            "applied_at": application["applied_at"]
        })
    return results


# Recruiter accepts or rejects an application
@router.patch("/{application_id}/status")
def update_application_status(application_id: str,data: ApplicationStatusUpdate,current_user: dict = Depends(get_current_user)):
    require_role(current_user, "recruiter")

    if not ObjectId.is_valid(application_id):
        raise HTTPException(
            status_code=400,
            detail="Invalid application ID"
        )

    application = applications_collection.find_one({
        "_id": ObjectId(application_id),
        "recruiter_id": current_user["user_id"]
    })

    if not application:
        raise HTTPException(
            status_code=404,
            detail="Application not found"
        )

    if application["status"] != "applied":
        raise HTTPException(
            status_code=409,
            detail="This application has already been reviewed"
        )

    applications_collection.update_one(
        {"_id": ObjectId(application_id)},
        {"$set": {"status": data.status}}
    )

    job = jobs_collection.find_one({
        "_id": ObjectId(application["job_id"])
    })

    job_title = job.get("title", "the job") if job else "the job"

    notification = {
        "user_id": application["candidate_id"],
        "message": (
            f"Your application for {job_title} "
            f"has been {data.status}."
        ),
        "type": data.status,
        "read": False,
        "created_at": datetime.now(timezone.utc).isoformat()
    }

    notifications_collection.insert_one(notification)

    return {
        "message": f"Application {data.status} successfully",
        "application_id": application_id,
        "status": data.status
    }
# Recruiter views applications for a specific job
@router.get("/job/{job_id}")
def get_job_applications(job_id: str,current_user: dict = Depends(get_current_user)):
    require_role(current_user, "recruiter")

    if not ObjectId.is_valid(job_id):
        raise HTTPException(status_code=400,detail="Invalid job ID")
    job = jobs_collection.find_one({
        "_id": ObjectId(job_id),
        "recruiter_id": current_user["user_id"]
    })

    if not job:
        raise HTTPException(
            status_code=404,
            detail="Job not found"
        )

    applications = list(
        applications_collection.find({
            "job_id": job_id,
            "recruiter_id": current_user["user_id"]
        }).sort("applied_at", -1)
    )

    results = []

    for application in applications:
        candidate = users_collection.find_one(
            {"_id": ObjectId(application["candidate_id"])},
            {"name": 1, "email": 1}
        )

        results.append({
            "application_id": str(application["_id"]),
            "candidate_id": application["candidate_id"],
            "candidate_name": candidate.get("name", "") if candidate else "",
            "candidate_email": candidate.get("email", "") if candidate else "",
            "status": application["status"],
            "applied_at": application["applied_at"]
        })

    return {
        "job_id": job_id,
        "job_title": job.get("title", ""),
        "applications": results
    }