package com.univault.repository;

import com.univault.entity.Folder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FolderRepository extends JpaRepository<Folder, Long> {
    List<Folder> findByUserIdAndParentFolderIsNull(Long userId);
    List<Folder> findByParentFolderId(Long folderId);
}