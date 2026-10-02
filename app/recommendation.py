def calculate_match(profile: dict, job: dict) -> dict:
    candidate_skills = {
        skill.strip().lower()
        for skill in profile.get("skills", [])
    }

    required_skills = {
        skill.strip().lower()
        for skill in job.get("required_skills", [])
    } 
    matched_skills = candidate_skills & required_skills
    missing_skills = required_skills - candidate_skills

    # Calculate skill score
    if required_skills:
        skill_score = (
            len(matched_skills) / len(required_skills)
        ) * 60
    else:
        skill_score = 0

    # Calculate location score
    preferred_locations = {
        location.strip().lower()
        for location in profile.get("preferred_locations", [])
    }

    job_location = job.get("location", "").strip().lower()

    location_score = (
        20 if job_location in preferred_locations else 0
    )

    # Calculate job type score
    preferred_job_type = (
        profile.get("preferred_job_type", "").strip().lower()
    )

    job_type = job.get("job_type", "").strip().lower()

    job_type_score = (
        20 if preferred_job_type == job_type else 0
    )

    # Calculate total score
    total_score = skill_score + location_score + job_type_score

    return {
        "match_percentage": round(total_score, 2),
        "matched_skills": sorted(matched_skills),
        "missing_skills": sorted(missing_skills)
    }