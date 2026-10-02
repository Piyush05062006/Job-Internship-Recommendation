import os
from fastapi import APIRouter, HTTPException,Header
from app.database import users_collection
from app.models import UserRegister, UserLogin
from app.auth import (
    hash_password,
    verify_password,
    create_access_token
)
RECRUITER_SECRET = os.getenv("RECRUITER_SECRET")

router = APIRouter(
    prefix="/auth",
    tags=["Authentication"]
)

@router.post("/register")
def register(user: UserRegister):
    existing_user = users_collection.find_one({
        "email": user.email.lower()
    })
    if existing_user:
        raise HTTPException(status_code=409,detail="Email already registered")
    hashed_password = hash_password(user.password)

    new_user = {
        "name": user.name,
        "email": user.email.lower(),
        "password": hashed_password,
        "role": "candidate"
    }
    result = users_collection.insert_one(new_user)
    return {
        "message": "Registration successful",
        "user_id": str(result.inserted_id),
        "role": "candidate"
    }


@router.post("/login")
def login(user: UserLogin):

    existing_user = users_collection.find_one({
        "email": user.email.lower()
    })

    if not existing_user:
        raise HTTPException(
            status_code=401,
            detail="Invalid email or password"
        )

    if not verify_password(
        user.password,
        existing_user["password"]
    ):
        raise HTTPException(
            status_code=401,
            detail="Invalid email or password"
        )

    token = create_access_token(
        str(existing_user["_id"]),
        existing_user["role"]
    )

    return {
        "access_token": token,
        "token_type": "bearer"
    }

@router.post("/recruiter/register")
def register_recruiter(
    user: UserRegister,
    recruiter_secret: str = Header(...)
):
    if recruiter_secret != RECRUITER_SECRET:
        raise HTTPException(
            status_code=403,
            detail="Invalid recruiter secret"
        )

    existing_user = users_collection.find_one({
        "email": user.email.lower()
    })

    if existing_user:
        raise HTTPException(
            status_code=409,
            detail="Email already registered"
        )

    hashed_password = hash_password(user.password)

    new_user = {
        "name": user.name,
        "email": user.email.lower(),
        "password": hashed_password,
        "role": "recruiter"
    }

    result = users_collection.insert_one(new_user)

    return {
        "message": "Recruiter registration successful",
        "user_id": str(result.inserted_id),
        "role": "recruiter"
    }