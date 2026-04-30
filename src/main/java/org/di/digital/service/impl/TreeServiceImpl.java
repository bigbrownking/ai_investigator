package org.di.digital.service.impl;

import lombok.RequiredArgsConstructor;
import org.di.digital.service.TreeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TreeServiceImpl implements TreeService {
    @Value("${qualification.model.host}")
    private String pythonHost;

    @Value("${tree.port}")
    private String treePort;


}
